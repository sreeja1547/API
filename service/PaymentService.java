package com.ecommerce.productordermanagement.service;

import com.ecommerce.productordermanagement.dto.PaymentRequest;
import com.ecommerce.productordermanagement.dto.PaymentQrRequest;
import com.ecommerce.productordermanagement.dto.PaymentQrResult;
import com.ecommerce.productordermanagement.dto.PaymentResult;
import com.ecommerce.productordermanagement.dto.PaymentStatusResult;

public interface PaymentService {

    PaymentResult process(PaymentRequest request);

    PaymentQrResult createUpiQr(PaymentQrRequest request);

    PaymentStatusResult checkStatus(String paymentReference);
}