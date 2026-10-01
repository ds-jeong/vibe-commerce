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

    /**
     * 반품 승인 전용. 기존 cancelPayment는 변경하지 않는다.
     * 실패 시 false 또는 예외로 호출 측에 알려 DB 저장을 건너뛰게 한다.
     */
    public boolean cancelPaymentForReturnRefund(Payment payment, String reason) {
        if (payment == null) {
            throw new IllegalStateException("환불 대상 결제 정보가 없습니다.");
        }
        String paymentId = firstNonBlank(payment.getPgPaymentId(), payment.getPgImpUid());
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalStateException("환불 가능한 PG 결제 ID가 없습니다.");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("PortOne API 시크릿이 설정되지 않았습니다.");
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("PortOne 환불 API가 실패 응답을 반환했습니다. (" + code + ")");
            }
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PortOne 환불 API 통신 중 오류가 발생했습니다.", e);
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
