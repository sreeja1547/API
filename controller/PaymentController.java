package com.ecommerce.productordermanagement.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.PaymentQrRequest;
import com.ecommerce.productordermanagement.dto.PaymentQrResult;
import com.ecommerce.productordermanagement.dto.PaymentStatusResult;
import com.ecommerce.productordermanagement.service.PaymentService;
import com.ecommerce.productordermanagement.service.SessionUserService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final SessionUserService sessionUserService;

    public PaymentController(PaymentService paymentService, SessionUserService sessionUserService) {
        this.paymentService = paymentService;
        this.sessionUserService = sessionUserService;
    }

    @PostMapping("/qr")
    public PaymentQrResult createQr(@Valid @RequestBody PaymentQrRequest request,
                                    HttpSession session) {
        sessionUserService.requireUser(session);
        return paymentService.createUpiQr(request);
    }

    @GetMapping("/status")
    public PaymentStatusResult checkStatus(@RequestParam String reference, HttpSession session) {
        sessionUserService.requireUser(session);
        return paymentService.checkStatus(reference);
    }
}