package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import com.claudiocarige.backendcreditengine.pricing.domain.PricingValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CommercialTermCalculator — Testes unitários de prazo comercial")
class CommercialTermCalculatorTest {

    private CommercialTermCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CommercialTermCalculator();
    }

    @ParameterizedTest(name = "{0} dias corridos ({1} a {2}) deve resultar em {3} mês(es)")
    @CsvSource({
        "1, 2026-11-04, 2026-11-05, 1",
        "29, 2026-11-04, 2026-12-03, 1",
        "30, 2026-11-04, 2026-12-04, 1",
        "31, 2026-11-04, 2026-12-05, 2",
        "59, 2026-11-04, 2027-01-02, 2",
        "60, 2026-11-04, 2027-01-03, 2",
        "61, 2026-11-04, 2027-01-04, 3",
        "89, 2026-11-04, 2027-02-01, 3",
        "90, 2026-11-04, 2027-02-02, 3",
        "91, 2026-11-04, 2027-02-03, 4"
    })
    void shouldCalculateCommercialTermMonths(long expectedDays, LocalDate opDate, LocalDate dueDate, int expectedMonths) {
        int termMonths = calculator.calculateTermMonths(opDate, dueDate);
        assertEquals(expectedMonths, termMonths);
    }

    @Test
    @DisplayName("Deve calcular corretamente em virada de mês (31/01 a 28/02)")
    void shouldCalculateAcrossMonthBoundary() {
        LocalDate opDate = LocalDate.of(2026, 1, 31);
        LocalDate dueDate = LocalDate.of(2026, 2, 28);
        assertEquals(1, calculator.calculateTermMonths(opDate, dueDate));
    }

    @Test
    @DisplayName("Deve calcular corretamente em ano bissexto (fevereiro com 29 dias)")
    void shouldCalculateInLeapYear() {
        LocalDate opDate = LocalDate.of(2024, 2, 1);
        LocalDate dueDate = LocalDate.of(2024, 3, 1); 
        assertEquals(1, calculator.calculateTermMonths(opDate, dueDate));

        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        LocalDate nextDay = LocalDate.of(2024, 3, 1); 
        assertEquals(1, calculator.calculateTermMonths(leapDay, nextDay));
    }

    @Test
    @DisplayName("Deve funcionar com datas derivadas de Clock fixo")
    void shouldWorkWithClockControlledDates() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
        LocalDate opDate = LocalDate.now(fixedClock);
        LocalDate dueDate = opDate.plusDays(90);

        assertEquals(3, calculator.calculateTermMonths(opDate, dueDate));
    }

    @Test
    @DisplayName("Deve lançar exceção quando dueDate for igual a operationDate")
    void shouldThrowWhenDueDateIsEqualToOperationDate() {
        LocalDate date = LocalDate.of(2026, 11, 4);
        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> calculator.calculateTermMonths(date, date)
        );
        assertEquals("Due date must be strictly after operation date", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção quando dueDate for anterior a operationDate")
    void shouldThrowWhenDueDateIsBeforeOperationDate() {
        LocalDate opDate = LocalDate.of(2026, 11, 4);
        LocalDate dueDate = LocalDate.of(2026, 11, 3);
        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> calculator.calculateTermMonths(opDate, dueDate)
        );
        assertEquals("Due date must be strictly after operation date", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção quando operationDate for nula")
    void shouldThrowWhenOperationDateIsNull() {
        LocalDate dueDate = LocalDate.of(2026, 11, 5);
        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> calculator.calculateTermMonths(null, dueDate)
        );
        assertEquals("Operation date must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção quando dueDate for nula")
    void shouldThrowWhenDueDateIsNull() {
        LocalDate opDate = LocalDate.of(2026, 11, 4);
        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> calculator.calculateTermMonths(opDate, null)
        );
        assertEquals("Due date must not be null", exception.getMessage());
    }
}

