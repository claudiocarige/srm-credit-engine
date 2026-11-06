package com.claudiocarige.backendcreditengine.assignor.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade JPA representativa de cedentes (assignors).
 * Mapeamento append-only/leitura inicial marcado como imutável no Hibernate.
 */
@Entity
@Table(name = "assignors")
@Immutable
public class Assignor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document", nullable = false, length = 14, updatable = false)
    private String document;

    @Column(name = "name", nullable = false, length = 150, updatable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Assignor() { }

    public Assignor(
            String document,
            String name,
            Instant createdAt) {

        Objects.requireNonNull(document);
        Objects.requireNonNull(name);

        if (!document.matches("^[0-9]{11,14}$")) {
            throw new IllegalArgumentException(
                    "Invalid assignor document"
            );
        }

        if (name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException(
                    "Invalid assignor name"
            );
        }

        this.document = document;
        this.name = name;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null")
                .truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getDocument() {
        return document;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

