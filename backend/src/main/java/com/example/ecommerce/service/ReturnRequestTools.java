package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReturnRequestTools {

    public static final String EXPLAIN_RETURN_POLICY = "explain_return_policy";
    public static final String CHECK_RETURN_ELIGIBILITY = "check_return_eligibility";
    public static final String SUBMIT_RETURN_REQUEST = "submit_return_request";
    public static final String CONFIRM_PHRASE = "반품 신청 확인";

    private static final Logger log = LoggerFactory.getLogger(ReturnRequestTools.class);
    private static final Set<String> SAFE_MESSAGES = Set.of(
            "배송완료 주문만 반품 신청할 수 있습니다.",
            "이미 진행 중인 반품 신청이 있습니다.",
            "반품 사유는 1000자까지 입력할 수 있습니다.",
            "회원 정보를 찾을 수 없습니다.");

    private final OrderLookupTools orderLookupTools;
    private final ReturnRequestService returnRequestService;
    private final ObjectMapper objectMapper;

    public ReturnRequestTools(
            OrderLookupTools orderLookupTools,
            ReturnRequestService returnRequestService,
            ObjectMapper objectMapper) {
        this.orderLookupTools = orderLookupTools;
        this.returnRequestService = returnRequestService;
        this.objectMapper = objectMapper;
    }

    public static boolean userConfirmed(String userMessage) {
        return userMessage != null && userMessage.contains(CONFIRM_PHRASE);
    }

    public boolean handles(String name) {
        return EXPLAIN_RETURN_POLICY.equals(name)
                || CHECK_RETURN_ELIGIBILITY.equals(name)
                || SUBMIT_RETURN_REQUEST.equals(name);
    }

    public List<Map<String, Object>> definitions() {
        return List.of(
                function(EXPLAIN_RETURN_POLICY,
                        "코드에 있는 반품 규칙만 설명합니다. 개별 주문의 반품 가능 여부는 판단하지 않습니다.",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
                function(CHECK_RETURN_ELIGIBILITY,
                        "로그인한 회원의 주문 한 건이 현재 반품 신청 가능한지 서버 규칙으로 확인합니다. 신청은 생성하지 않습니다.",
                        orderNumberSchema()),
                function(SUBMIT_RETURN_REQUEST,
                        "사용자가 반품 신청 확인이라고 답한 뒤에만 본인 주문의 반품 신청을 접수합니다. 환불과 결제 취소는 하지 않습니다.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "orderNumber", Map.of("type", "string", "description", "본인 주문번호"),
                                        "reason", Map.of("type", "string", "description", "사용자가 말한 반품 사유"),
                                        "confirmed", Map.of("type", "boolean", "description", "사용자가 반품 신청 확인이라고 답했으면 true")),
                                "required", List.of("orderNumber", "reason", "confirmed"),
                                "additionalProperties", false)));
    }

    public String execute(String name, String argumentsJson, String userMessage) {
        try {
            if (EXPLAIN_RETURN_POLICY.equals(name)) {
                orderLookupTools.currentUserKey();
                return explain();
            }
            if (CHECK_RETURN_ELIGIBILITY.equals(name)) {
                return check(argumentsJson);
            }
            if (SUBMIT_RETURN_REQUEST.equals(name)) {
                return submit(argumentsJson, userMessage);
            }
            return write(Map.of("accepted", false, "message", "지원하지 않는 반품 기능입니다."));
        } catch (IllegalArgumentException exception) {
            return write(Map.of("accepted", false, "message", "로그인 후 이용해 주세요."));
        } catch (RuntimeException exception) {
            log.warn("반품 도구 실패");
            return write(Map.of("accepted", false, "message", "반품 신청을 처리하지 못했습니다."));
        }
    }

    private String explain() {
        Map<String, Object> facts = new LinkedHashMap<>(ReturnPolicyFacts.describe());
        facts.put("summary", """
                회원 본인 주문만 신청할 수 있습니다. 신청 가능한 주문 상태는 eligibleOrderStatuses에 있는 값뿐입니다.
                blockingReturnStatuses에 있는 반품 요청이 있으면 중복 신청할 수 없습니다.
                접수되면 주문 상태는 acceptedOrderStatus, 반품 요청은 acceptedReturnStatus가 됩니다.
                이 접수는 환불, 결제 취소, 재고 변경을 실행하지 않습니다.
                notDefinedInCode에 있는 조건은 정하지 않습니다.
                이 설명만으로 특정 주문의 반품 가능 여부를 확정하지 않습니다.
                """.trim());
        return write(facts);
    }

    private String check(String argumentsJson) {
        String userKey = orderLookupTools.currentUserKey();
        String orderNumber = readText(argumentsJson, "orderNumber");
        if (orderNumber == null) {
            return write(Map.of("found", false, "accepted", false, "message", "주문번호를 확인할 수 없습니다."));
        }
        Orders order = orderLookupTools.findOwnedOrder(orderNumber);
        if (order == null) {
            return notFound();
        }
        try {
            returnRequestService.assertMemberCanRequest(userKey, order.getId());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return write(eligibility(order, false, safeMessage(exception)));
        }
        return write(eligibility(order, true, "현재 규칙으로는 반품 신청이 가능합니다. 아직 접수되지 않았습니다."));
    }

    private String submit(String argumentsJson, String userMessage) {
        String userKey = orderLookupTools.currentUserKey();
        JsonNode root = readRoot(argumentsJson);
        boolean confirmed = root != null && root.path("confirmed").asBoolean(false);
        if (!confirmed || !userConfirmed(userMessage)) {
            return write(Map.of("accepted", false, "message", "사용자 확인 전에는 반품을 접수할 수 없습니다."));
        }
        String orderNumber = readText(root, "orderNumber");
        String reason = readText(root, "reason");
        if (orderNumber == null) {
            return write(Map.of("accepted", false, "message", "주문번호를 확인할 수 없습니다."));
        }
        if (reason == null) {
            return write(Map.of("accepted", false, "message", "반품 사유를 확인할 수 없습니다."));
        }
        Orders order = orderLookupTools.findOwnedOrder(orderNumber);
        if (order == null) {
            return notFound();
        }
        try {
            Orders saved = returnRequestService.submitMemberReturn(userKey, order.getId(), reason);
            log.info("반품 신청 접수 orderId={}", saved.getId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accepted", true);
            body.put("orderNumber", order.getOrderMerchantUid() == null ? orderNumber : order.getOrderMerchantUid());
            body.put("orderStatus", saved.getStatus() == null ? null : saved.getStatus().name());
            body.put("returnStatus", "REQUESTED");
            body.put("refundExecuted", false);
            body.put("paymentCancelExecuted", false);
            body.put("stockChanged", false);
            return write(body);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accepted", false);
            body.put("message", safeMessage(exception));
            body.put("refundExecuted", false);
            body.put("paymentCancelExecuted", false);
            return write(body);
        }
    }

    private Map<String, Object> eligibility(Orders order, boolean eligible, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("found", true);
        body.put("eligible", eligible);
        body.put("orderNumber", order.getOrderMerchantUid());
        body.put("status", order.getStatus() == null ? null : order.getStatus().name());
        body.put("items", itemNames(order));
        body.put("message", message);
        body.put("accepted", false);
        return body;
    }

    private List<String> itemNames(Orders order) {
        List<String> names = new ArrayList<>();
        if (order.getOrderItems() == null) {
            return names;
        }
        for (OrderItem item : order.getOrderItems()) {
            if (item == null) {
                continue;
            }
            Product product = item.getProduct();
            if (product != null && product.getName() != null) {
                names.add(product.getName());
            }
        }
        return names;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (SAFE_MESSAGES.contains(message)) {
            return message;
        }
        return "해당 주문을 찾을 수 없습니다.";
    }

    private String notFound() {
        return write(Map.of("found", false, "accepted", false, "message", "해당 주문을 찾을 수 없습니다."));
    }

    private String readText(String argumentsJson, String field) {
        return readText(readRoot(argumentsJson), field);
    }

    private String readText(JsonNode root, String field) {
        if (root == null) {
            return null;
        }
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private JsonNode readRoot(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(argumentsJson);
            return root.isObject() ? root : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> orderNumberSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderNumber", Map.of("type", "string", "description", "본인 주문번호. 사용자 식별자는 넣지 않습니다.")),
                "required", List.of("orderNumber"),
                "additionalProperties", false);
    }

    private Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of("name", name, "description", description, "parameters", parameters));
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.copy()
                    .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                    .writeValueAsString(body);
        } catch (Exception exception) {
            log.warn("반품 도구 결과 작성 실패");
            return "{\"accepted\":false,\"message\":\"반품 신청을 처리하지 못했습니다.\"}";
        }
    }
}
