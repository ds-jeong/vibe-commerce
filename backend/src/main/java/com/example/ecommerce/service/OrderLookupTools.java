package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderLookupTools {

    public static final String LIST_MY_ORDERS = "list_my_orders";
    public static final String GET_MY_ORDER = "get_my_order";
    static final int MAX_ORDERS = 10;

    private static final Logger log = LoggerFactory.getLogger(OrderLookupTools.class);
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderLookupTools(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> definitions() {
        return List.of(
                function(LIST_MY_ORDERS,
                        "로그인한 회원의 최근 주문을 조회합니다. 인자로 사용자를 지정할 수 없습니다.",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                function(GET_MY_ORDER,
                        "로그인한 회원의 주문 한 건과 배송 상태를 조회합니다. 배송사 실시간 위치는 제공하지 않습니다.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "orderNumber", Map.of(
                                                "type", "string",
                                                "description", "본인 주문번호. 사용자 식별자는 넣지 않습니다.")),
                                "required", List.of("orderNumber"),
                                "additionalProperties", false)));
    }

    public String currentUserKey() {
        return requireUserKey();
    }

    public Orders findOwnedOrder(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }
        return findOwned(orderService.getMyOrders(requireUserKey()), orderNumber.trim());
    }

    public String execute(String name, String argumentsJson) {
        try {
            String userKey = requireUserKey();
            if (LIST_MY_ORDERS.equals(name)) {
                return list(userKey);
            }
            if (GET_MY_ORDER.equals(name)) {
                return get(userKey, argumentsJson);
            }
            return error("지원하지 않는 조회입니다.");
        } catch (IllegalArgumentException exception) {
            return error("로그인 후 이용해 주세요.");
        } catch (RuntimeException exception) {
            log.warn("주문 조회 도구 실패");
            return error("주문 조회 중 문제가 생겼습니다.");
        }
    }

    private String list(String userKey) {
        List<Orders> orders;
        try {
            orders = orderService.getMyOrders(userKey);
        } catch (IllegalArgumentException exception) {
            return error("회원 주문을 확인할 수 없습니다.");
        }
        if (orders == null) {
            orders = List.of();
        }
        boolean truncated = orders.size() > MAX_ORDERS;
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Orders order : orders.subList(0, Math.min(orders.size(), MAX_ORDERS))) {
            summaries.add(summarize(order));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", summaries);
        body.put("total", orders.size());
        body.put("truncated", truncated);
        if (orders.isEmpty()) {
            body.put("message", "조회된 주문이 없습니다.");
        }
        return write(body);
    }

    private String get(String userKey, String argumentsJson) {
        String orderNumber = readOrderNumber(argumentsJson);
        if (orderNumber == null) {
            return write(Map.of("found", false, "message", "주문번호를 확인할 수 없습니다."));
        }
        List<Orders> orders;
        try {
            orders = orderService.getMyOrders(userKey);
        } catch (IllegalArgumentException exception) {
            return error("회원 주문을 확인할 수 없습니다.");
        }
        Orders match = findOwned(orders, orderNumber);
        if (match == null) {
            return write(Map.of("found", false, "message", "해당 주문을 찾을 수 없습니다."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("found", true);
        body.put("order", summarize(match));
        return write(body);
    }

    private Orders findOwned(List<Orders> orders, String orderNumber) {
        if (orders == null) {
            return null;
        }
        for (Orders order : orders) {
            if (order != null && orderNumber.equals(order.getOrderMerchantUid())) {
                return order;
            }
        }
        for (Orders order : orders) {
            if (order != null && order.getId() != null && orderNumber.equals(String.valueOf(order.getId()))) {
                return order;
            }
        }
        return null;
    }

    private String readOrderNumber(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(argumentsJson);
            JsonNode orderNumber = root.get("orderNumber");
            if (orderNumber == null || !orderNumber.isTextual() || orderNumber.asText().isBlank()) {
                return null;
            }
            return orderNumber.asText().trim();
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> summarize(Orders order) {
        Map<String, Object> row = new LinkedHashMap<>();
        String orderNumber = order.getOrderMerchantUid();
        if (orderNumber == null || orderNumber.isBlank()) {
            orderNumber = order.getId() == null ? null : String.valueOf(order.getId());
        }
        row.put("orderNumber", orderNumber);
        row.put("status", order.getStatus() == null ? null : order.getStatus().name());
        row.put("statusLabel", statusLabel(order.getStatus()));
        row.put("beforeShipping", isBeforeShipping(order.getStatus()));
        row.put("orderDate", order.getOrderDate() == null ? null : ORDER_DATE.format(order.getOrderDate()));
        String trackingNumber = blankToNull(order.getTrackingNumber());
        row.put("trackingNumber", trackingNumber);
        row.put("liveCarrierStatus", null);
        row.put("deliveredAt", null);
        row.put("netAmount", order.getNetAmount());
        row.put("items", items(order));
        return row;
    }

    private List<Map<String, Object>> items(Orders order) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getOrderItems() == null) {
            return items;
        }
        for (OrderItem item : order.getOrderItems()) {
            if (item == null) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            Product product = item.getProduct();
            line.put("name", product == null ? null : product.getName());
            line.put("count", item.getCount());
            items.add(line);
        }
        return items;
    }

    private static boolean isBeforeShipping(OrderStatus status) {
        return status == OrderStatus.ORDERED || status == OrderStatus.PAID || status == OrderStatus.PREPARING;
    }

    private static String statusLabel(OrderStatus status) {
        if (status == null) {
            return "상태 없음";
        }
        return switch (status) {
            case ORDERED -> "주문 접수";
            case PAID -> "결제 완료";
            case PREPARING -> "배송 준비";
            case SHIPPING -> "배송 중";
            case DELIVERING -> "배달 중";
            case DELIVERED -> "배송 완료";
            case CANCELLED -> "주문 취소";
            case RETURN_REQUESTED -> "반품 요청";
            case RETURNED -> "반품 완료";
            case REFUND_REQUESTED -> "환불 요청";
            case REFUNDED -> "환불 완료";
        };
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof String userKey)) {
            throw new IllegalArgumentException("로그인 후 이용해 주세요.");
        }
        if (userKey.isBlank() || "anonymousUser".equals(userKey)) {
            throw new IllegalArgumentException("로그인 후 이용해 주세요.");
        }
        return userKey;
    }

    private Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of("name", name, "description", description, "parameters", parameters));
    }

    private String error(String message) {
        return write(Map.of("error", message));
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.copy()
                    .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                    .writeValueAsString(body);
        } catch (Exception exception) {
            log.warn("주문 조회 결과 작성 실패");
            return "{\"error\":\"주문 조회 중 문제가 생겼습니다.\"}";
        }
    }
}
