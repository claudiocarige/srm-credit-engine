package com.claudiocarige.backendcreditengine.receivable.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.claudiocarige.backendcreditengine.assignor.application.exception.AssignorNotFoundException;
import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.assignor.persistence.AssignorRepository;
import com.claudiocarige.backendcreditengine.common.exception.InvalidDueDateException;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.application.exception.DuplicateReceivableDocumentException;
import com.claudiocarige.backendcreditengine.receivable.application.exception.ReceivableNotFoundException;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import com.claudiocarige.backendcreditengine.receivable.persistence.Receivable;
import com.claudiocarige.backendcreditengine.receivable.persistence.ReceivableRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:20:00Z");
    private static final UUID ASSIGNOR_ID = UUID.fromString("8f14e45f-ceea-467a-9a38-1b4c9f8b0001");

    @Mock
    private ReceivableRepository receivableRepository;

    @Mock
    private AssignorRepository assignorRepository;

    private Clock fixedClock;
    private ReceivableService receivableService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        receivableService = new ReceivableService(receivableRepository, assignorRepository, fixedClock);
    }

    @Test
    @DisplayName("Deve criar recebível normalizando documentNumber (trim + UPPERCASE) com status OPEN e createdAt do Clock")
    void shouldCreateReceivableNormalizingDocumentNumberSuccessfully() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        LocalDate dueDate = LocalDate.of(2027, 2, 2);
        BigDecimal faceValueBrl = new BigDecimal("100000.00");

        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.of(assignor));
        when(receivableRepository.existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ID, "DUP-2026-0042")).thenReturn(false);
        when(receivableRepository.saveAndFlush(any(Receivable.class)))
                .thenAnswer(inv -> inv.getArgument(0, Receivable.class));

        Receivable created = receivableService.createReceivable(
                ASSIGNOR_ID,
                "  dup-2026-0042  ",
                ReceivableType.DUPLICATA_MERCANTIL,
                faceValueBrl,
                dueDate
        );

        ArgumentCaptor<Receivable> captor = ArgumentCaptor.forClass(Receivable.class);
        verify(receivableRepository).saveAndFlush(captor.capture());

        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getAssignor()).isSameAs(assignor);
        assertThat(created.getDocumentNumber()).isEqualTo("DUP-2026-0042");
        assertThat(created.getType()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
        assertThat(created.getFaceValueBrl()).isEqualByComparingTo("100000.00");
        assertThat(created.getDueDate()).isEqualTo(dueDate);
        assertThat(created.getStatus()).isEqualTo(ReceivableStatus.OPEN);
        assertThat(created.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("Deve rejeitar dueDate igual à operationDate com InvalidDueDateException sem consultar banco")
    void shouldRejectDueDateEqualToOperationDate() {
        LocalDate sameDay = LocalDate.of(2026, 11, 4);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "DUP-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                sameDay
        ))
                .isInstanceOf(InvalidDueDateException.class)
                .hasMessageContaining("Due date (2026-11-04) must be strictly after operation date (2026-11-04)");

        verifyNoInteractions(assignorRepository, receivableRepository);
    }

    @Test
    @DisplayName("Deve rejeitar dueDate anterior à operationDate com InvalidDueDateException sem consultar banco")
    void shouldRejectDueDateBeforeOperationDate() {
        LocalDate pastDay = LocalDate.of(2026, 11, 3);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "DUP-2026-0042",
                ReceivableType.CHEQUE_PRE_DATADO,
                new BigDecimal("50000.00"),
                pastDay
        ))
                .isInstanceOf(InvalidDueDateException.class)
                .hasMessageContaining("Due date (2026-11-03) must be strictly after operation date (2026-11-04)");

        verifyNoInteractions(assignorRepository, receivableRepository);
    }

    @Test
    @DisplayName("Deve lançar AssignorNotFoundException quando assignorId não existe no cadastro de recebível")
    void shouldThrowAssignorNotFoundWhenAssignorDoesNotExistOnCreate() {
        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "DUP-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2)
        ))
                .isInstanceOf(AssignorNotFoundException.class)
                .hasMessageContaining(ASSIGNOR_ID.toString());

        verify(receivableRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deve lançar DuplicateReceivableDocumentException na checagem prévia quando (assignorId, documentNumber) já existe")
    void shouldThrowDuplicateReceivableDocumentOnPreCheck() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.of(assignor));
        when(receivableRepository.existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ID, "DUP-2026-0042")).thenReturn(true);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                " dup-2026-0042 ",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2)
        ))
                .isInstanceOf(DuplicateReceivableDocumentException.class)
                .hasMessageContaining("DUP-2026-0042")
                .hasMessageContaining(ASSIGNOR_ID.toString());

        verify(receivableRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deve traduzir DataIntegrityViolationException para DuplicateReceivableDocumentException quando PSQLException possui SQLState=23505 e constraint=uk_receivables_assignor_document_number")
    void shouldTranslatePostgresUniqueConstraintViolationToDuplicateReceivableDocumentException() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.of(assignor));
        when(receivableRepository.existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ID, "DUP-2026-0042")).thenReturn(false);

        ServerErrorMessage serverErrorMessage = mock(ServerErrorMessage.class);
        when(serverErrorMessage.getSQLState()).thenReturn("23505");
        when(serverErrorMessage.getConstraint()).thenReturn("uk_receivables_assignor_document_number");

        PSQLException psqlException = new PSQLException(serverErrorMessage);
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "could not execute statement", psqlException
        );

        when(receivableRepository.saveAndFlush(any(Receivable.class))).thenThrow(dive);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "dup-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2)
        ))
                .isInstanceOf(DuplicateReceivableDocumentException.class)
                .hasCause(dive);
    }

    @Test
    @DisplayName("Não deve mascarar DataIntegrityViolationException quando constraint é diferente mesmo com SQLState 23505")
    void shouldRethrowDataIntegrityViolationFromDifferentConstraint() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.of(assignor));
        when(receivableRepository.existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ID, "DUP-2026-0042")).thenReturn(false);

        ServerErrorMessage serverErrorMessage = mock(ServerErrorMessage.class);
        when(serverErrorMessage.getSQLState()).thenReturn("23505");
        when(serverErrorMessage.getConstraint()).thenReturn("uk_other_constraint");

        PSQLException psqlException = new PSQLException(serverErrorMessage);
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "unique violation on another constraint", psqlException
        );

        when(receivableRepository.saveAndFlush(any(Receivable.class))).thenThrow(dive);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "dup-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2)
        ))
                .isSameAs(dive);
    }

    @Test
    @DisplayName("Não deve mascarar DataIntegrityViolationException de outro SQLState")
    void shouldRethrowDataIntegrityViolationFromDifferentSqlState() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        when(assignorRepository.findById(ASSIGNOR_ID)).thenReturn(Optional.of(assignor));
        when(receivableRepository.existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ID, "DUP-2026-0042")).thenReturn(false);

        ServerErrorMessage serverErrorMessage = mock(ServerErrorMessage.class);
        when(serverErrorMessage.getSQLState()).thenReturn("23503");

        PSQLException psqlException = new PSQLException(serverErrorMessage);
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "foreign key violation", psqlException
        );

        when(receivableRepository.saveAndFlush(any(Receivable.class))).thenThrow(dive);

        assertThatThrownBy(() -> receivableService.createReceivable(
                ASSIGNOR_ID,
                "dup-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2)
        ))
                .isSameAs(dive);
    }

    @Test
    @DisplayName("Deve listar recebíveis por assignorId e status delegando ordenação dueDate ASC, documentNumber ASC")
    void shouldListReceivablesByAssignorAndStatus() {
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        Receivable r1 = new Receivable(
                assignor,
                "DUP-2026-0001",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("10000.00"),
                LocalDate.of(2026, 12, 10),
                FIXED_INSTANT
        );
        when(assignorRepository.existsById(ASSIGNOR_ID)).thenReturn(true);
        when(receivableRepository.findByAssignor_IdAndStatusOrderByDueDateAscDocumentNumberAsc(
                ASSIGNOR_ID, ReceivableStatus.OPEN
        )).thenReturn(List.of(r1));

        List<Receivable> result = receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN);

        assertThat(result).containsExactly(r1);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando cedente existe mas não possui recebíveis no status filtrado")
    void shouldReturnEmptyListWhenAssignorExistsWithoutReceivables() {
        when(assignorRepository.existsById(ASSIGNOR_ID)).thenReturn(true);
        when(receivableRepository.findByAssignor_IdAndStatusOrderByDueDateAscDocumentNumberAsc(
                ASSIGNOR_ID, ReceivableStatus.SETTLED
        )).thenReturn(List.of());

        List<Receivable> result = receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.SETTLED);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve lançar AssignorNotFoundException ao listar recebíveis de cedente inexistente")
    void shouldThrowAssignorNotFoundWhenListingForNonExistentAssignor() {
        when(assignorRepository.existsById(ASSIGNOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN))
                .isInstanceOf(AssignorNotFoundException.class)
                .hasMessageContaining(ASSIGNOR_ID.toString());

        verifyNoInteractions(receivableRepository);
    }

    @Test
    @DisplayName("Deve buscar recebível por id com sucesso ou lançar ReceivableNotFoundException quando inexistente")
    void shouldGetReceivableByIdOrThrowWhenNotFound() {
        UUID receivableId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        Assignor assignor = createAssignor(ASSIGNOR_ID);
        Receivable receivable = new Receivable(
                assignor,
                "DUP-2026-0042",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2),
                FIXED_INSTANT
        );
        when(receivableRepository.findById(receivableId)).thenReturn(Optional.of(receivable));

        assertThat(receivableService.getById(receivableId)).isSameAs(receivable);

        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(receivableRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receivableService.getById(missingId))
                .isInstanceOf(ReceivableNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    private Assignor createAssignor(UUID id) {
        Assignor assignor = new Assignor("12345678000190", "Industria Alfa Ltda", FIXED_INSTANT);
        ReflectionTestUtils.setField(assignor, "id", id);
        return assignor;
    }
}
