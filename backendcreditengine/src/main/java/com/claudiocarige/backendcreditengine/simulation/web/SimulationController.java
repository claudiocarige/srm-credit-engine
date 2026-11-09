package com.claudiocarige.backendcreditengine.simulation.web;

import com.claudiocarige.backendcreditengine.simulation.application.SimulationResult;
import com.claudiocarige.backendcreditengine.simulation.application.SimulationService;
import com.claudiocarige.backendcreditengine.simulation.web.dto.SimulationRequest;
import com.claudiocarige.backendcreditengine.simulation.web.dto.SimulationResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public ResponseEntity<SimulationResponse> simulate(@Valid @RequestBody SimulationRequest request) {
        SimulationResult result = simulationService.simulate(
                request.type(),
                new BigDecimal(request.faceValueBrl()),
                request.dueDate(),
                request.paymentCurrency()
        );
        return ResponseEntity.ok(SimulationResponse.from(result));
    }
}
