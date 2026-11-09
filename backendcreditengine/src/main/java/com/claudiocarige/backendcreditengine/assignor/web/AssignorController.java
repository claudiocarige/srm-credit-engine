package com.claudiocarige.backendcreditengine.assignor.web;

import com.claudiocarige.backendcreditengine.assignor.application.AssignorService;
import com.claudiocarige.backendcreditengine.assignor.web.dto.AssignorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignors")
public class AssignorController {

    private final AssignorService assignorService;

    public AssignorController(AssignorService assignorService) {
        this.assignorService = assignorService;
    }

    @GetMapping
    public ResponseEntity<List<AssignorResponse>> listAssignors() {
        List<AssignorResponse> response = assignorService.listAssignors().stream()
                .map(AssignorResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
