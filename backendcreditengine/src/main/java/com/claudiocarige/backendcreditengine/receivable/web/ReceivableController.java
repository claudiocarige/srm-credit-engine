package com.claudiocarige.backendcreditengine.receivable.web;

import com.claudiocarige.backendcreditengine.receivable.application.ReceivableService;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import com.claudiocarige.backendcreditengine.receivable.persistence.Receivable;
import com.claudiocarige.backendcreditengine.receivable.web.dto.CreateReceivableRequest;
import com.claudiocarige.backendcreditengine.receivable.web.dto.ReceivableResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivables")
public class ReceivableController {

    private final ReceivableService receivableService;

    public ReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    @PostMapping
    public ResponseEntity<ReceivableResponse> createReceivable(@Valid @RequestBody CreateReceivableRequest request) {
        Receivable created = receivableService.createReceivable(
                request.assignorId(),
                request.documentNumber(),
                request.type(),
                new BigDecimal(request.faceValueBrl()),
                request.dueDate()
        );
        URI location = URI.create("/api/v1/receivables/" + created.getId());
        return ResponseEntity.created(location).body(ReceivableResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<List<ReceivableResponse>> listReceivables(
            @RequestParam UUID assignorId,
            @RequestParam(defaultValue = "OPEN") ReceivableStatus status
    ) {
        List<ReceivableResponse> response = receivableService.listByAssignor(assignorId, status).stream()
                .map(ReceivableResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceivableResponse> getReceivableById(@PathVariable UUID id) {
        Receivable receivable = receivableService.getById(id);
        return ResponseEntity.ok(ReceivableResponse.from(receivable));
    }
}
