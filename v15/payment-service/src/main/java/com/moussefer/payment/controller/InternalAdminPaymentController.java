package com.moussefer.payment.controller;

import com.moussefer.payment.dto.response.PaymentResponse;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/internal/admin")
@RequiredArgsConstructor
public class InternalAdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping("/all")
    public ResponseEntity<Page<PaymentResponse>> listAll(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminPaymentService.listPayments(status, passengerId, from, to, pageable));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable String paymentId) {
        return ResponseEntity.ok(adminPaymentService.getById(paymentId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(adminPaymentService.getFinancialStats(from, to));
    }
}