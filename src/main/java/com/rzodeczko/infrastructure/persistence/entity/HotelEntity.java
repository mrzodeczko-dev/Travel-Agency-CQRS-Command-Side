package com.rzodeczko.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="hotels")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HotelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hotels_seq")
    @SequenceGenerator(
            name = "hotels_seq",
            sequenceName = "hotels_seq")
    @EqualsAndHashCode.Include
    private Long id;

    private Integer capacity;
}
