package com.carevoice.controller;

import com.carevoice.service.AccountService;
import com.carevoice.service.MonitoringPlanProposalService;
import com.carevoice.dto.proposal.AddQuestionRequest;
import com.carevoice.dto.proposal.ProposalSummary;
import com.carevoice.dto.proposal.ProposalView;
import com.carevoice.dto.proposal.ReorderRequest;
import com.carevoice.dto.proposal.UpdateQuestionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/clinician")
public class ClinicianPlanProposalController {
    private final MonitoringPlanProposalService proposals;
    private final AccountService accounts;

    public ClinicianPlanProposalController(MonitoringPlanProposalService proposals, AccountService accounts) {
        this.proposals = proposals;
        this.accounts = accounts;
    }

    @GetMapping("/patients/{patientId}/monitoring-plan-proposals")
    @PreAuthorize("hasRole('CLINICIAN')")
    public List<ProposalSummary> list(@PathVariable Long patientId) {
        return proposals.list(patientId);
    }

    @PostMapping("/patients/{patientId}/monitoring-plan-proposals")
    @PreAuthorize("hasRole('CLINICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView generate(@PathVariable Long patientId, Authentication authentication) {
        return proposals.generate(patientId, accounts.current(authentication));
    }

    @GetMapping("/monitoring-plan-proposals/{proposalId}")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ProposalView get(@PathVariable Long proposalId) {
        return proposals.get(proposalId);
    }

    @PutMapping("/monitoring-plan-proposals/{proposalId}/questions/{questionId}")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ProposalView updateQuestion(
            @PathVariable Long proposalId,
            @PathVariable Long questionId,
            @RequestBody UpdateQuestionRequest request) {
        return proposals.updateQuestion(
                proposalId, questionId, request.questionText(), request.required(), request.enabled());
    }

    @PostMapping("/monitoring-plan-proposals/{proposalId}/questions")
    @PreAuthorize("hasRole('CLINICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView addQuestion(@PathVariable Long proposalId, @RequestBody AddQuestionRequest request) {
        if (request == null || request.fieldCode() == null || request.fieldCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Choose a supported monitoring field.");
        }
        return proposals.addQuestion(proposalId, request.fieldCode(), request.questionText(),
                request.required() == null || request.required());
    }

    @PutMapping("/monitoring-plan-proposals/{proposalId}/question-order")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ProposalView reorder(@PathVariable Long proposalId, @RequestBody ReorderRequest request) {
        return proposals.reorder(proposalId, request == null ? null : request.questionIds());
    }

    @PostMapping("/monitoring-plan-proposals/{proposalId}/approve")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ProposalView approve(@PathVariable Long proposalId, Authentication authentication) {
        return proposals.approve(proposalId, accounts.current(authentication));
    }

    @PostMapping("/monitoring-plan-proposals/{proposalId}/reject")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ProposalView reject(@PathVariable Long proposalId) {
        return proposals.reject(proposalId);
    }
}
