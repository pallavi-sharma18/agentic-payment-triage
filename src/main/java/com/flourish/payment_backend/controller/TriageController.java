package com.flourish.payment_backend.controller;

import com.flourish.payment_backend.dtos.ActionPlanDto;
import com.flourish.payment_backend.dtos.TriageResultDto;
import com.flourish.payment_backend.service.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TriageController {
    private final TriageService triageService;

    @PostMapping("/triage")
    public TriageResultDto triage(@RequestParam String paymentId) {
        return triageService.triage(paymentId);
    }

    @PostMapping("/triage/{paymentId}/execute")
    public String execute(@PathVariable String paymentId,
                          @RequestBody ActionPlanDto plan,
                          @RequestParam(defaultValue = "") List<Integer> approveSteps) {
        return triageService.executeRemediation(paymentId, plan, new HashSet<>(approveSteps));
    }
}
