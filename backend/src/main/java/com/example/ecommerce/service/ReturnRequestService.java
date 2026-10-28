package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderLifecycle;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ReturnRequestService {

    private static final Set<OrderStatus> BLOCKED_ORDER_STATUSES = Set.of(
            OrderStatus.CANCELLED,
            OrderStatus.RETURNED,
            OrderStatus.REFUNDED
    );

    private final UserRepository userRepository;
    private final OrdersRepository ordersRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ProductRepository productRepository;

    public ReturnRequestService(
            UserRepository userRepository,
            OrdersRepository ordersRepository,
            ReturnRequestRepository returnRequestRepository,
            ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.ordersRepository = ordersRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.productRepository = productRepository;
    }

    public Orders submitMemberReturn(String username, Long orderId) {
        return submitMemberReturn(username, orderId, null);
    }

    public Orders submitMemberReturn(String username, Long orderId, String reason) {
        User user = userRepository.findByUserKey(username)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        Orders order = ordersRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        assertOwnedAndReturnable(user, order);

        ReturnRequest request = new ReturnRequest();
        request.setOrder(order);
        request.setStatus(ReturnStatus.REQUESTED);
        request.setRequestedAt(LocalDateTime.now());
        request.setReason(normalizeReason(reason));
        returnRequestRepository.save(request);

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        ordersRepository.save(order);
        return ordersRepository.findWithItemsById(order.getId()).orElse(order);
    }

    @Transactional(readOnly = true)
    public void assertMemberCanRequest(String username, Long orderId) {
        User user = userRepository.findByUserKey(username)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        Orders order = ordersRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        assertOwnedAndReturnable(user, order);
    }

    private void assertOwnedAndReturnable(User user, Orders order) {
        if (order.getUser() == null || order.getUser().getId() == null
                || !order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인 주문만 처리할 수 있습니다.");
        }
        if (!OrderLifecycle.canRequestReturn(order.getStatus())) {
            throw new IllegalStateException("배송완료 주문만 반품 신청할 수 있습니다.");
        }
        if (returnRequestRepository.existsByOrder_IdAndStatusIn(order.getId(), ReturnStatus.activeStatuses())) {
            throw new IllegalStateException("이미 진행 중인 반품 신청이 있습니다.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 1000) {
            throw new IllegalArgumentException("반품 사유는 1000자까지 입력할 수 있습니다.");
        }
        return trimmed;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminReturns() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReturnRequest request : returnRequestRepository.findAllWithOrderItems()) {
            rows.add(toAdminRow(request.getOrder(), request, "RETURN_REQUEST"));
        }
        for (Orders order : ordersRepository.findLegacyReturnRequestedWithoutReturnRequest(OrderStatus.RETURN_REQUESTED)) {
            rows.add(toAdminRow(order, null, "LEGACY_ORDER"));
        }
        rows.sort(Comparator.comparing(
                row -> String.valueOf(row.get("orderDate") == null ? "" : row.get("orderDate")),
                Comparator.reverseOrder()));
        return rows;
    }

    public Map<String, Object> approve(Long orderId) {
        Orders order = lockProcessableOrder(orderId);
        ReturnRequest request = requireOrAttachLegacy(order, ReturnStatus.REQUESTED);
        transition(request, ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        ordersRepository.save(order);
        return toAdminRow(reload(order.getId()), request, "RETURN_REQUEST");
    }

    public Map<String, Object> reject(Long orderId, String reason) {
        Orders order = lockProcessableOrder(orderId);
        ReturnRequest request = requireOrAttachLegacy(order, ReturnStatus.REQUESTED);
        if (reason != null && !reason.isBlank()) {
            request.setReason(reason.trim());
        }
        transition(request, ReturnStatus.REQUESTED, ReturnStatus.REJECTED);
        order.setStatus(OrderStatus.DELIVERED);
        ordersRepository.save(order);
        return toAdminRow(reload(order.getId()), request, "RETURN_REQUEST");
    }

    public Map<String, Object> receive(Long orderId) {
        Orders order = lockProcessableOrder(orderId);
        ReturnRequest request = requireExisting(order, ReturnStatus.APPROVED);
        transition(request, ReturnStatus.APPROVED, ReturnStatus.RECEIVED);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        ordersRepository.save(order);
        return toAdminRow(reload(order.getId()), request, "RETURN_REQUEST");
    }

    public Map<String, Object> mockComplete(Long orderId) {
        Orders order = lockProcessableOrder(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new IllegalStateException("이미 모의 환불이 완료된 주문입니다.");
        }
        ReturnRequest request = requireExisting(order, ReturnStatus.RECEIVED);
        int updated = returnRequestRepository.updateStatusIfMatch(
                request.getId(), ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
        if (updated != 1) {
            throw new IllegalStateException("반품 상태를 변경할 수 없습니다. 이미 처리되었을 수 있습니다.");
        }
        request.setStatus(ReturnStatus.COMPLETED);
        Orders withItems = ordersRepository.findWithItemsById(order.getId()).orElse(order);
        restoreStockOnce(withItems);
        withItems.setStatus(OrderStatus.REFUNDED);
        ordersRepository.save(withItems);
        return toAdminRow(withItems, request, "RETURN_REQUEST");
    }

    private Orders lockProcessableOrder(Long orderId) {
        Orders order = ordersRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (BLOCKED_ORDER_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("이 주문 상태에서는 신규 반품 처리를 할 수 없습니다.");
        }
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED
                && !returnRequestRepository.existsByOrder_IdAndStatusIn(order.getId(), ReturnStatus.activeStatuses())) {
            throw new IllegalStateException("기존 REFUND_REQUESTED 주문은 상태를 덮어쓰지 않습니다.");
        }
        return order;
    }

    private ReturnRequest requireExisting(Orders order, ReturnStatus expected) {
        ReturnRequest request = returnRequestRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), Set.of(expected))
                .orElseThrow(() -> new IllegalStateException("해당 단계의 반품 요청이 없습니다."));
        if (request.getStatus() == ReturnStatus.COMPLETED) {
            throw new IllegalStateException("완료된 반품 요청은 다시 처리할 수 없습니다.");
        }
        if (request.getStatus() != expected) {
            throw new IllegalStateException("허용되지 않은 반품 상태 전이입니다.");
        }
        return request;
    }

    private ReturnRequest requireOrAttachLegacy(Orders order, ReturnStatus expected) {
        return returnRequestRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), Set.of(expected))
                .orElseGet(() -> attachLegacyRequested(order, expected));
    }

    private ReturnRequest attachLegacyRequested(Orders order, ReturnStatus expected) {
        if (expected != ReturnStatus.REQUESTED || order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new IllegalStateException("허용되지 않은 반품 상태 전이입니다.");
        }
        if (returnRequestRepository.existsByOrder_IdAndStatusIn(order.getId(), ReturnStatus.activeStatuses())) {
            throw new IllegalStateException("이미 진행 중인 반품 신청이 있습니다.");
        }
        ReturnRequest created = new ReturnRequest();
        created.setOrder(order);
        created.setStatus(ReturnStatus.REQUESTED);
        created.setRequestedAt(LocalDateTime.now());
        return returnRequestRepository.save(created);
    }

    private void transition(ReturnRequest request, ReturnStatus expected, ReturnStatus next) {
        int updated = returnRequestRepository.updateStatusIfMatch(request.getId(), expected, next);
        if (updated != 1) {
            throw new IllegalStateException("반품 상태를 변경할 수 없습니다. 이미 처리되었을 수 있습니다.");
        }
        request.setStatus(next);
    }

    private void restoreStockOnce(Orders order) {
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
            product.setStockQuantity(Math.max(current + count, 0));
            productRepository.save(product);
        }
    }

    private Orders reload(Long orderId) {
        return ordersRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private Map<String, Object> toAdminRow(Orders order, ReturnRequest request, String source) {
        Map<String, Object> row = new HashMap<>();
        if (order != null) {
            row.put("id", order.getId());
            row.put("orderMerchantUid", order.getOrderMerchantUid());
            row.put("status", order.getStatus() == null ? null : order.getStatus().name());
            row.put("totalAmount", order.getTotalAmount());
            row.put("netAmount", order.getNetAmount());
            row.put("orderDate", order.getOrderDate());
            row.put("trackingNumber", order.getTrackingNumber());
            List<Map<String, Object>> items = new ArrayList<>();
            if (order.getOrderItems() != null) {
                for (OrderItem item : order.getOrderItems()) {
                    if (item == null) {
                        continue;
                    }
                    Map<String, Object> line = new HashMap<>();
                    line.put("id", item.getId());
                    line.put("count", item.getCount());
                    line.put("orderPrice", item.getOrderPrice());
                    Product product = item.getProduct();
                    Map<String, Object> productView = new HashMap<>();
                    if (product != null) {
                        productView.put("id", product.getId());
                        productView.put("name", product.getName());
                        productView.put("imageUrl", product.getImageUrl());
                    }
                    line.put("product", productView);
                    items.add(line);
                }
            }
            row.put("orderItems", items);
        }
        row.put("source", source);
        row.put("legacyIncomplete", "LEGACY_ORDER".equals(source));
        row.put("returnRequestId", request == null ? null : request.getId());
        row.put("returnStatus", request == null ? ReturnStatus.REQUESTED.name() : request.getStatus().name());
        row.put("reason", request == null ? null : request.getReason());
        return row;
    }
}
