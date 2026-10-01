package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderLifecycle;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrdersRepository ordersRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PortOneRefundService portOneRefundService;

    public Map<String, Object> createOrder(Map<String, Object> params) {
        return createOrder(params, null);
    }

    public Map<String, Object> createOrder(Map<String, Object> params, String username) {
        String merchantUid = "VIBE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Orders order = new Orders();
        order.setOrderMerchantUid(merchantUid);
        order.setStatus(OrderStatus.ORDERED);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setOrderDate(LocalDateTime.now());
        order.setNonUserName((String) params.get("ordererName"));

        String rawPassword = (String) params.get("phoneNumber");
        if (rawPassword != null && !rawPassword.isBlank()) {
            order.setNonUserPassword(passwordEncoder.encode(rawPassword));
        }

        if (username != null && !username.isBlank() && !"anonymousUser".equals(username)) {
            userRepository.findByUserKey(username).ifPresent(order::setUser);
        }

        BigDecimal computedAmount = resolveOrderAmount(params, order);
        order.setTotalAmount(computedAmount);
        order.setNetAmount(computedAmount);

        ordersRepository.save(order);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("merchantUid", merchantUid);
        result.put("amount", computedAmount);
        return result;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal resolveOrderAmount(Map<String, Object> params, Orders order) {
        Object itemsObj = params.get("items");

        if (itemsObj instanceof List<?> rawItems && !rawItems.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();

            for (Object rawItem : rawItems) {
                if (!(rawItem instanceof Map<?, ?> itemMap)) {
                    continue;
                }

                Object idObj = itemMap.get("id") != null ? itemMap.get("id") : itemMap.get("productId");
                Object qtyObj = itemMap.get("quantity") != null ? itemMap.get("quantity") : itemMap.get("count");

                if (idObj == null) {
                    throw new IllegalArgumentException("주문 상품 ID가 누락되었습니다.");
                }

                Long productId = ((Number) idObj).longValue();
                int quantity = qtyObj == null ? 1 : ((Number) qtyObj).intValue();
                if (quantity <= 0) {
                    throw new IllegalArgumentException("주문 수량이 올바르지 않습니다.");
                }

                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + productId));

                BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                total = total.add(lineAmount);

                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(product);
                orderItem.setOrderPrice(product.getPrice());
                orderItem.setCount(quantity);
                orderItems.add(orderItem);
            }

            if (orderItems.isEmpty()) {
                throw new IllegalArgumentException("주문 상품 목록이 비어 있습니다.");
            }

            order.setOrderItems(orderItems);
            return total;
        }

        if (params.get("price") == null) {
            throw new IllegalArgumentException("결제 금액 정보가 누락되었습니다.");
        }

        return new BigDecimal(params.get("price").toString());
    }

    public void verifyAndCompletePayment(Map<String, Object> params) {
        String merchantUid = (String) params.get("merchantUid");

        if (params.get("totalAmount") == null) {
            throw new IllegalArgumentException("결제 금액 정보가 누락되었습니다.");
        }
        BigDecimal paidAmount = new BigDecimal(params.get("totalAmount").toString());

        Orders order = ordersRepository.findWithItemsByOrderMerchantUid(merchantUid)
                .orElseGet(() -> ordersRepository.findByOrderMerchantUid(merchantUid)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 번호입니다.")));

        if (order.getNetAmount().compareTo(paidAmount) != 0) {
            order.setStatus(OrderStatus.CANCELLED);
            ordersRepository.save(order);
            throw new IllegalStateException("결제 금액 불일치로 주문 취소 처리");
        }

        order.setStatus(OrderStatus.PAID);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPgImpUid((String) params.get("txId"));
        Object paymentId = params.get("paymentId");
        if (paymentId != null) {
            payment.setPgPaymentId(String.valueOf(paymentId));
        }
        payment.setPgProvider("KAKAO_PAY");
        payment.setPayMethod("EASY_PAY");
        payment.setAmount(paidAmount);
        payment.setPaidAt(LocalDateTime.now());

        paymentRepository.save(payment);
        restoreOrConsumeStock(order, false);
    }

    @Transactional(readOnly = true)
    public Orders verifyNonUserOrder(Map<String, String> params) {
        Orders order = ordersRepository.findWithItemsByOrderMerchantUid(params.get("orderMerchantUid"))
                .orElseThrow(() -> new IllegalArgumentException("주문번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(params.get("password"), order.getNonUserPassword())) {
            throw new IllegalArgumentException("비밀번호 불일치");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Orders> getMyOrders(String username) {
        User user = userRepository.findByUserKey(username)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        List<Orders> orders = ordersRepository.findWithItemsByUser(user);
        orders.sort(Comparator.comparing(Orders::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return orders;
    }

    @Transactional(readOnly = true)
    public Orders lookupGuestOrder(Map<String, String> params) {
        String ordererName = params == null ? null : params.get("ordererName");
        String phoneNumber = params == null ? null : params.get("phoneNumber");

        if (ordererName == null || ordererName.isBlank() || phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("주문자 이름과 휴대폰 번호를 입력해주세요.");
        }

        List<Orders> candidates = ordersRepository.findByNonUserNameOrderByOrderDateDesc(ordererName.trim());

        Orders matched = candidates.stream()
                .filter(order -> order.getNonUserPassword() != null
                        && passwordEncoder.matches(phoneNumber, order.getNonUserPassword()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("일치하는 비회원 주문을 찾을 수 없습니다."));

        return ordersRepository.findWithItemsById(matched.getId()).orElse(matched);
    }

    public Orders cancelMyOrder(String username, Long orderId) {
        Orders order = getOwnedOrder(username, orderId);
        if (!OrderLifecycle.canUserCancel(order.getStatus())) {
            throw new IllegalStateException("결제 직후 상태에서만 즉시 취소할 수 있습니다.");
        }
        refundAndRestore(order, "고객 즉시 주문취소");
        order.setStatus(OrderStatus.CANCELLED);
        return ordersRepository.save(order);
    }

    public Orders requestReturn(String username, Long orderId) {
        Orders order = getOwnedOrder(username, orderId);
        if (!OrderLifecycle.canRequestReturn(order.getStatus())) {
            throw new IllegalStateException("배송완료 주문만 반품 신청할 수 있습니다.");
        }
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        return ordersRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Orders> getAllOrdersForAdmin() {
        List<Orders> orders = ordersRepository.findAllWithItems();
        orders.sort(Comparator.comparing(Orders::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return orders;
    }

    public Orders updateAdminOrderStatus(Long orderId, Map<String, String> payload) {
        Orders order = ordersRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        String nextStatusRaw = payload == null ? null : payload.get("status");
        String trackingNumber = payload == null ? null : payload.get("trackingNumber");

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            order.setTrackingNumber(trackingNumber.trim());
        }

        if (nextStatusRaw == null || nextStatusRaw.isBlank()) {
            return ordersRepository.save(order);
        }

        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(nextStatusRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 주문 상태입니다.");
        }

        if (nextStatus == OrderStatus.SHIPPING) {
            if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
                throw new IllegalArgumentException("배송 시작 전 운송장 번호가 필요합니다.");
            }
        }

        if (nextStatus == OrderStatus.RETURNED || nextStatus == OrderStatus.REFUNDED) {
            if (!OrderLifecycle.canApproveReturn(order.getStatus())) {
                throw new IllegalStateException("반품 신청 건만 반품완료 처리할 수 있습니다.");
            }
            refundAndRestore(order, "관리자 반품 승인");
        }

        order.setStatus(nextStatus);
        return ordersRepository.save(order);
    }

    private Orders getOwnedOrder(String username, Long orderId) {
        User user = userRepository.findByUserKey(username)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        Orders order = ordersRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (order.getUser() == null || order.getUser().getId() == null
                || !order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인 주문만 처리할 수 있습니다.");
        }
        return order;
    }

    private void refundAndRestore(Orders order, String reason) {
        paymentRepository.findByOrder(order)
                .ifPresent(payment -> portOneRefundService.cancelPayment(payment, reason));
        restoreOrConsumeStock(order, true);
    }

    private void restoreOrConsumeStock(Orders order, boolean restore) {
        if (order == null || order.getOrderItems() == null) {
            return;
        }
        for (OrderItem item : order.getOrderItems()) {
            if (item == null || item.getProduct() == null) {
                continue;
            }
            Product product = item.getProduct();
            int current = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            int count = item.getCount() == null ? 0 : item.getCount();
            int next = restore ? current + count : current - count;
            product.setStockQuantity(Math.max(next, 0));
            productRepository.save(product);
        }
    }
}
