package com.example.ecommerce.domain;

import java.util.EnumSet;
import java.util.Set;

public final class OrderLifecycle {

    private static final Set<OrderStatus> USER_CANCELABLE =
            EnumSet.of(OrderStatus.ORDERED, OrderStatus.PAID);

    private static final Set<OrderStatus> PREPARING_OR_LATER =
            EnumSet.of(
                    OrderStatus.PREPARING,
                    OrderStatus.SHIPPING,
                    OrderStatus.DELIVERING,
                    OrderStatus.DELIVERED,
                    OrderStatus.RETURN_REQUESTED,
                    OrderStatus.RETURNED
            );

    private OrderLifecycle() {
    }

    public static OrderStatus safe(OrderStatus status) {
        return status == null ? OrderStatus.ORDERED : status;
    }

    public static boolean canUserCancel(OrderStatus status) {
        return USER_CANCELABLE.contains(safe(status));
    }

    public static boolean isPreparingOrLater(OrderStatus status) {
        return PREPARING_OR_LATER.contains(safe(status));
    }

    public static boolean canRequestReturn(OrderStatus status) {
        return safe(status) == OrderStatus.DELIVERED;
    }

    public static boolean canApproveReturn(OrderStatus status) {
        OrderStatus current = safe(status);
        return current == OrderStatus.RETURN_REQUESTED || current == OrderStatus.REFUND_REQUESTED;
    }
}
