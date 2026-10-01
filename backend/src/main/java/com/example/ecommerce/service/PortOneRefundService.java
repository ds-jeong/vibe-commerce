package com.example.ecommerce.service;

import com.example.ecommerce.domain.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class PortOneRefundService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${portone.api-secret:}")
    private String apiSecret;

    public void cancelPayment(Payment payment, String reason) {
        if (payment == null) {
            return;
        }

        String paymentId = firstNonBlank(payment.getPgPaymentId(), payment.getPgImpUid());
        if (paymentId == null || apiSecret == null || apiSecret.isBlank()) {
            return;
        }

        try {
            String body = "{\"reason\":\"" + escape(reason) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.portone.io/payments/" + paymentId + "/cancel"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "PortOne " + apiSecret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // 로컬/시크릿 미설정 환경에서도 DB 취소 흐름이 막히지 않도록 한다.
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String escape(String value) {
        if (value == null) {
            return "고객 요청 취소";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
