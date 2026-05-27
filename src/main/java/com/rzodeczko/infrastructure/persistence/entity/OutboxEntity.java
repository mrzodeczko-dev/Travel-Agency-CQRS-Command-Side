package com.rzodeczko.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    private String aggregateId;

    private String type;

    @Lob
    private String payload;
    private LocalDateTime createdAt;


    @Builder.Default
    @Column(nullable = false)
    private Integer retryCount = 0;

    public void incrementRetryCount() {
        if (retryCount == null) {
            retryCount = 0;
        }
        retryCount += 1;
    }

    public boolean hasExceededMaxRetries(int maxRetries) {
        return retryCount != null && retryCount >= maxRetries;
    }
}
