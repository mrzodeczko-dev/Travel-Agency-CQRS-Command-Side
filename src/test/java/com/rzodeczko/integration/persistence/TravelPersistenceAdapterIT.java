package com.rzodeczko.integration.persistence;

import com.rzodeczko.domain.exception.OverbookingException;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.BookingStatus;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.adapter.TravelPersistenceAdapter;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityId;
import com.rzodeczko.infrastructure.persistence.repository.JpaDailyAvailabilityRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import com.rzodeczko.integration.AbstractIntegrationTest;
import com.rzodeczko.integration.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@SpringBootTest
@Sql(scripts = "classpath:truncate.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TravelPersistenceAdapterIT extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2027, 7, 1);
    private static final LocalDate DATE_END = LocalDate.of(2027, 7, 3);

    @Autowired
    private TravelPersistenceAdapter adapter;

    @Autowired
    private JpaDailyAvailabilityRepository dailyAvailabilityRepository;

    @Autowired
    private JpaOutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    //  CRUD

    @Test
    void saveHotel_andFindById_roundTrips() {
        Hotel saved = adapter.saveHotel(new Hotel(null, 42L));

        Optional<Hotel> found = adapter.findHotel(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCapacity()).isEqualTo(42L);
    }

    @Test
    void saveBooking_andFindById_roundTrips() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 10L));
        Booking booking = new Booking(null, hotel.getId(), 1L, DATE, DATE_END);

        Booking saved = adapter.save(booking);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(BookingStatus.ACTIVE);

        Optional<Booking> found = adapter.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().hotelId()).isEqualTo(hotel.getId());
    }

    @Test
    void saveOutbox_persistsEntry() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 5L));
        Booking booking = adapter.save(new Booking(null, hotel.getId(), 1L, DATE, DATE_END));

        adapter.saveOutbox(booking);

        assertThat(outboxRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(entry -> {
                    assertThat(entry.getType()).isEqualTo("BookingCreated");
                    assertThat(entry.getAggregateId()).isEqualTo(hotel.getId().toString());
                    assertThat(entry.getPayload()).contains(booking.id().toString());
                });
    }

    //  Availability

    @Test
    void reserveAvailability_createsSlots() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 10L));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                adapter.reserveAvailability(hotel.getId(), 10L, DATE, DATE_END));

        assertThat(dailyAvailabilityRepository.findAll()).hasSize(3);
        dailyAvailabilityRepository.findAll().forEach(slot ->
                assertThat(slot.getOccupiedRooms()).isEqualTo(1));
    }

    @Test
    void reserveAvailability_atCapacity_throwsOverbookingException() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 1L));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(s -> adapter.reserveAvailability(hotel.getId(), 1L, DATE, DATE));

        assertThatThrownBy(() ->
                tx.executeWithoutResult(s -> adapter.reserveAvailability(hotel.getId(), 1L, DATE, DATE)))
                .isInstanceOf(OverbookingException.class);
    }

    @Test
    void releaseAvailability_decrementsOccupiedRooms() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 5L));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(s -> adapter.reserveAvailability(hotel.getId(), 5L, DATE, DATE));
        tx.executeWithoutResult(s -> adapter.reserveAvailability(hotel.getId(), 5L, DATE, DATE));
        tx.executeWithoutResult(s -> adapter.releaseAvailability(hotel.getId(), DATE, DATE));

        DailyAvailabilityEntity slot = dailyAvailabilityRepository
                .findById(new DailyAvailabilityId(hotel.getId(), DATE))
                .orElseThrow();
        assertThat(slot.getOccupiedRooms()).isEqualTo(1);
    }

    // Pessimistic locking

    @Test
    void concurrentReservations_pessimisticLock_preventsOverbooking() throws Exception {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 1L));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Runnable task = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                tx.executeWithoutResult(status ->
                        adapter.reserveAvailability(hotel.getId(), 1L, DATE, DATE));
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(task);
            executor.submit(task);
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes.get()).as("exactly one reservation should succeed").isEqualTo(1);
        assertThat(failures.get()).as("exactly one reservation should fail").isEqualTo(1);

        DailyAvailabilityEntity slot = dailyAvailabilityRepository
                .findById(new DailyAvailabilityId(hotel.getId(), DATE))
                .orElseThrow();
        assertThat(slot.getOccupiedRooms())
                .as("database should reflect only the successful reservation")
                .isEqualTo(1);
    }
}
