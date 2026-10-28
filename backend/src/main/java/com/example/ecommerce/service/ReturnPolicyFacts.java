package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderLifecycle;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.ReturnStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReturnPolicyFacts {

    private ReturnPolicyFacts() {
    }

    public static Map<String, Object> describe() {
        List<String> eligibleOrderStatuses = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            if (OrderLifecycle.canRequestReturn(status)) {
                eligibleOrderStatuses.add(status.name());
            }
        }
        List<String> blockingReturnStatuses = new ArrayList<>();
        for (ReturnStatus status : ReturnStatus.activeStatuses()) {
            blockingReturnStatuses.add(status.name());
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("source", "OrderLifecycle.canRequestReturn, ReturnRequestService.submitMemberReturn, ReturnStatus.activeStatuses");
        facts.put("eligibleOrderStatuses", List.copyOf(eligibleOrderStatuses));
        facts.put("blockingReturnStatuses", List.copyOf(blockingReturnStatuses));
        facts.put("acceptedOrderStatus", OrderStatus.RETURN_REQUESTED.name());
        facts.put("acceptedReturnStatus", ReturnStatus.REQUESTED.name());
        facts.put("refundExecuted", false);
        facts.put("paymentCancelExecuted", false);
        facts.put("stockChanged", false);
        facts.put("appliesToIndividualOrder", false);
        facts.put("notDefinedInCode", List.of("반품 가능 일수", "상품 개봉 또는 사용 여부", "부분 수량 반품"));
        return facts;
    }
}
