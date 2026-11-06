package com.claudiocarige.backendcreditengine.exchangerate.persistence;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExchangeRatePersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM exchange_rates");
    }

    @Test
    @DisplayName("Deve persistir e ler ExchangeRate com round-trip JPA preservando escala 6 e timestamps normalizados")
    void shouldPersistAndReadExchangeRateViaJpa() {
        Instant effectiveAt = Instant.parse("2026-11-06T09:00:00.123456Z");
        Instant createdAt = Instant.parse("2026-11-06T09:00:05.654321Z");
        BigDecimal rate = new BigDecimal("5.432100");

        transactionTemplate.executeWithoutResult(status -> {
            ExchangeRate exchangeRate = new ExchangeRate(
                    CurrencyCode.USD,
                    rate,
                    effectiveAt,
                    createdAt
            );

            entityManager.persist(exchangeRate);
            entityManager.flush();
            UUID id = exchangeRate.getId();
            assertThat(id).isNotNull();

            entityManager.clear();

            ExchangeRate found = entityManager.find(ExchangeRate.class, id);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getCurrency()).isEqualTo(CurrencyCode.USD);
            assertThat(found.getRateBrl()).isEqualByComparingTo("5.432100");
            assertThat(found.getEffectiveAt()).isEqualTo(effectiveAt.truncatedTo(ChronoUnit.MICROS));
            assertThat(found.getCreatedAt()).isEqualTo(createdAt.truncatedTo(ChronoUnit.MICROS));
        });
    }

    @Test
    @DisplayName("Deve rejeitar duplicidade de (currency, effective_at) violando uk_exchange_rates_currency_effective_at (23505)")
    void shouldRejectDuplicateCurrencyAndEffectiveAt() {
        Instant effectiveAt = Instant.parse("2026-11-06T08:00:00Z");

        jdbcTemplate.update("""
                INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                VALUES (?, 'USD', 5.500000, ?, now())
                """, UUID.randomUUID(), java.sql.Timestamp.from(effectiveAt));

        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                        VALUES (?, 'USD', 5.600000, ?, now())
                        """, UUID.randomUUID(), java.sql.Timestamp.from(effectiveAt)),
                "23505",
                "uk_exchange_rates_currency_effective_at"
        );
    }

    @Test
    @DisplayName("Deve rejeitar moeda diferente de USD violando ck_exchange_rates_currency (23514)")
    void shouldRejectCurrencyOtherThanUsdInDatabase() {
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                        VALUES (?, 'BRL', 1.000000, now(), now())
                        """, UUID.randomUUID()),
                "23514",
                "ck_exchange_rates_currency"
        );
    }

    @Test
    @DisplayName("Deve validar limites contratuais de rate_brl no banco: [0.000001, 9999999.999999] (ck_exchange_rates_rate_brl_range 23514)")
    void shouldValidateRateBrlRangeInDatabase() {
        // Rejeita 0.000000
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                        VALUES (?, 'USD', 0.000000, now(), now())
                        """, UUID.randomUUID()),
                "23514",
                "ck_exchange_rates_rate_brl_range"
        );

        // Rejeita 10000000.000000
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                        VALUES (?, 'USD', 10000000.000000, now(), now())
                        """, UUID.randomUUID()),
                "23514",
                "ck_exchange_rates_rate_brl_range"
        );

        // Aceita bordas 0.000001 e 9999999.999999
        int row1 = jdbcTemplate.update("""
                INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                VALUES (?, 'USD', 0.000001, '2026-11-06 01:00:00+00', now())
                """, UUID.randomUUID());
        assertThat(row1).isEqualTo(1);

        int row2 = jdbcTemplate.update("""
                INSERT INTO exchange_rates (id, currency, rate_brl, effective_at, created_at)
                VALUES (?, 'USD', 9999999.999999, '2026-11-06 02:00:00+00', now())
                """, UUID.randomUUID());
        assertThat(row2).isEqualTo(1);
    }

    private void assertConstraintViolation(Runnable action, String expectedSqlState, String expectedConstraint) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    PSQLException psql = extractPsqlException(ex);
                    assertThat((Throwable) psql).as("PSQLException esperada na cadeia de causas").isNotNull();
                    assertThat(psql.getSQLState()).isEqualTo(expectedSqlState);
                    if (expectedConstraint != null) {
                        assertThat(psql.getServerErrorMessage()).isNotNull();
                        assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo(expectedConstraint);
                    }
                });
    }

    private PSQLException extractPsqlException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof PSQLException psql) {
                return psql;
            }
            current = current.getCause();
        }
        return null;
    }
}
