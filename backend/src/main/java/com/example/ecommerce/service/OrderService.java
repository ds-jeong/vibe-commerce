package com.example.ecommerce.service;

import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrdersRepository ordersRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> createOrder(Map<String, Object> params) {
        String merchantUid = "VIBE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal price = new BigDecimal(params.get("price").toString());

        Orders order = new Orders();
        order.setOrderMerchantUid(merchantUid);
        order.setStatus(OrderStatus.ORDERED);
        order.setTotalAmount(price);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setNetAmount(price);

        order.setNonUserName((String) params.get("ordererName"));
        String rawPassword = (String) params.get("phoneNumber");
        order.setNonUserPassword(passwordEncoder.encode(rawPassword));

        ordersRepository.save(order);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("merchantUid", merchantUid);
        result.put("amount", price);
        return result;
    }

    public void verifyAndCompletePayment(Map<String, Object> params) {
            // 1. V2 스펙에 맞게 파라미터명 변경 및 Null 체크
    String paymentId = (String) params.get("paymentId"); // 리액트의 response.paymentId
    String merchantUid = (String) params.get("merchantUid"); 

    if (params.get("totalAmount") == null) {
        throw new IllegalArgumentException("결제 금액 정보가 누락되었습니다.");
    }
    BigDecimal paidAmount = new BigDecimal(params.get("totalAmount").toString());

    // 2. 주문 조회 (기존 merchantUid 대신 paymentId 사용 여부에 따라 레포지토리 메서드 매칭 필요)
    Orders order = ordersRepository.findByOrderMerchantUid(merchantUid)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 번호입니다."));

    // 3. 결제 금액 위변조 검증 (BigDecimal 비교는 compareTo를 사용하는 것이 안전합니다)
    if (order.getNetAmount().compareTo(paidAmount) != 0) {
        order.setStatus(OrderStatus.CANCELLED);
        ordersRepository.save(order); // 변경 상태 저장
        throw new IllegalStateException("결제 금액 불일치로 주문 취소 처리");
    }

    // 4. 주문 및 결제 정보 저장
    order.setStatus(OrderStatus.PAID);

    Payment payment = new Payment();
    payment.setOrder(order);
    payment.setPgImpUid((String) params.get("txId")); // V2에서는 txId가 결제 고유번호입니다.
    payment.setPgProvider("KAKAO_PAY");
    payment.setPayMethod("EASY_PAY"); // 카카오페이는 EASY_PAY(간편결제)에 해당합니다.
    payment.setAmount(paidAmount);
    payment.setPaidAt(LocalDateTime.now());

    paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Orders verifyNonUserOrder(Map<String, String> params) {
        Orders order = ordersRepository.findByOrderMerchantUid(params.get("orderMerchantUid"))
                .orElseThrow(() -> new IllegalArgumentException("주문번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(params.get("password"), order.getNonUserPassword())) {
            throw new IllegalArgumentException("비밀번호 불일치");
        }
        return order;
    }
}
