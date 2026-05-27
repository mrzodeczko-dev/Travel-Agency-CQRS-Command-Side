package com.rzodeczko.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_outbox")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DeadLetterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    private UUID originalOutboxId;
    private String aggregatedId;
    private String type;

    @Lob
    private String payload;

    @Lob
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime failedAt;
    private int retryCount;
}
