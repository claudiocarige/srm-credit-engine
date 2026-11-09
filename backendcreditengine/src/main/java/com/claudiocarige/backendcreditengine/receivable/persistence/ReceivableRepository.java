package com.claudiocarige.backendcreditengine.receivable.persistence;

import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {

    boolean existsByAssignor_IdAndDocumentNumber(UUID assignorId, String documentNumber);

    @EntityGraph(attributePaths = "assignor")
    List<Receivable> findByAssignor_IdAndStatusOrderByDueDateAscDocumentNumberAsc(
            UUID assignorId,
            ReceivableStatus status
    );

    @Override
    @EntityGraph(attributePaths = "assignor")
    Optional<Receivable> findById(UUID id);
}
