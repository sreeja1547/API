package com.ecommerce.productordermanagement.service.impl;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecommerce.productordermanagement.dto.PaymentQrRequest;
import com.ecommerce.productordermanagement.dto.PaymentQrResult;
import com.ecommerce.productordermanagement.dto.PaymentRequest;
import com.ecommerce.productordermanagement.dto.PaymentResult;
import com.ecommerce.productordermanagement.dto.PaymentStatusResult;
import com.ecommerce.productordermanagement.service.PaymentService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Pattern UPI_ID = Pattern.compile("^[A-Za-z0-9._-]+@[A-Za-z][A-Za-z0-9.-]*$");

    @Value("${app.payment.upi-id:}")
    private String merchantUpiId;

    @Value("${app.payment.merchant-name:Accersis Electronics}")
    private String merchantName;

    @Override
    public PaymentResult process(PaymentRequest request) {
        String method = normalizePaymentMethod(request == null ? null : request.paymentMethod());

        if ("COD".equals(method)) {
            return new PaymentResult("COD", "PENDING", null);
        }
        if (isDemoOnlineMethod(method)) {
            validatePaymentRequest(method, request);
            return new PaymentResult(method, "UNVERIFIED",
                    generateDemoTransactionId());
        }
        throw new IllegalArgumentException("Unsupported payment method: " + method);
    }

    @Override
    public PaymentQrResult createUpiQr(PaymentQrRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request is required.");
        }

        String method = normalizePaymentMethod(request.paymentMethod());
        if (!isQrPaymentMethod(method)) {
            throw new IllegalArgumentException("Unsupported payment method for QR generation: " + method);
        }

        BigDecimal amount = java.util.Objects.requireNonNull(request.amount(), "Payment amount is required.");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        BigDecimal normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        String reference = generateDemoTransactionId();
        String merchantIdentifier = resolveMerchantIdentifier();
        String demoMarker = "SHOPVERSE-DEMO-PAYMENT";
        String upiUri = "upi://pay?pa=" + encode(merchantIdentifier)
                + "&pn=" + encode(merchantName == null || merchantName.isBlank() ? "ShopVerse" : merchantName)
                + "&am=" + normalizedAmount.toPlainString()
                + "&cu=INR"
                + "&tn=" + encode(demoMarker + "-" + method + "-" + reference);
        return new PaymentQrResult(createQrDataUrl(upiUri), upiUri, reference,
                "UNVERIFIED", "Demo only: this app simulates a " + method + " payment and cannot verify real transfers.");
    }

    @Override
    public PaymentStatusResult checkStatus(String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank() || !paymentReference.startsWith("DEMO-")) {
            throw new IllegalArgumentException("Payment reference is invalid.");
        }
        return new PaymentStatusResult(paymentReference, false, "UNVERIFIED",
                "Demo only: the payment was simulated in the browser and cannot verify a real transfer.");
    }

    private String normalizePaymentMethod(String method) {
        if (method == null) {
            return "COD";
        }

        String normalized = method.trim();
        if (normalized.isEmpty()) {
            return "COD";
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("CASH_ON_DELIVERY".equals(upper) || "CASH".equals(upper) || "COD".equals(upper)) {
            return "COD";
        }
        if ("CARD".equals(upper) || "CREDIT_CARD".equals(upper) || "DEBIT_CARD".equals(upper)) {
            return upper.contains("CREDIT") ? "CREDIT_CARD" : "DEBIT_CARD";
        }
        if ("UPI".equals(upper) || "PHONEPE".equals(upper) || "PAYTM".equals(upper)) {
            return upper;
        }
        return upper;
    }

    private boolean isDemoOnlineMethod(String method) {
        return List.of("UPI", "PHONEPE", "PAYTM", "CREDIT_CARD", "DEBIT_CARD").contains(method);
    }

    private boolean isQrPaymentMethod(String method) {
        return List.of("UPI", "PHONEPE", "PAYTM").contains(method);
    }

    private String generateDemoTransactionId() {
        return "DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private String resolveMerchantIdentifier() {
        String configuredValue = merchantUpiId == null ? "" : merchantUpiId.trim();
        if (configuredValue.isEmpty()) {
            return "demo@shopverse";
        }
        if (!UPI_ID.matcher(configuredValue).matches()) {
            return "demo@shopverse";
        }
        return configuredValue;
    }

    private void validatePaymentRequest(String method, PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment details are required.");
        }

        boolean hasDemoValues = isBlank(request.upiId())
                && isBlank(request.cardNumber())
                && isBlank(request.expiryDate())
                && isBlank(request.cvv())
                && isBlank(request.cardHolderName());

        if (hasDemoValues) {
            return;
        }

        if ("UPI".equals(method) || "PHONEPE".equals(method) || "PAYTM".equals(method)) {
            String vpa = request.upiId() == null ? "" : request.upiId().trim();
            if (!vpa.isEmpty() && !UPI_ID.matcher(vpa).matches()) {
                throw new IllegalArgumentException("UPI ID is required and must be valid.");
            }
        }

        if ("CREDIT_CARD".equals(method) || "DEBIT_CARD".equals(method)) {
            String cardNumber = request.cardNumber() == null ? "" : request.cardNumber().replace(" ", "").trim();
            if (!cardNumber.isEmpty() && !cardNumber.matches("\\d{16}")) {
                throw new IllegalArgumentException("Card number must contain 16 digits.");
            }
            if (request.expiryDate() == null || request.expiryDate().isBlank()) {
                throw new IllegalArgumentException("Card expiry date is required.");
            }
            if (!isBlank(request.cvv()) && !request.cvv().matches("\\d{3,4}")) {
                throw new IllegalArgumentException("CVV is invalid.");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String createQrDataUrl(String contents) {
        try {
            var hints = java.util.Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE,
                    360, 360, hints);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "PNG", output);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Unable to generate UPI QR code.", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}