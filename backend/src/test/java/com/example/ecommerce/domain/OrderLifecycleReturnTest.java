package com.example.ecommerce.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleReturnTest {

    @Test
    void onlyDeliveredCanRequestReturn() {
        assertTrue(OrderLifecycle.canRequestReturn(OrderStatus.DELIVERED));
        assertFalse(OrderLifecycle.canRequestReturn(OrderStatus.PAID));
        assertFalse(OrderLifecycle.canRequestReturn(OrderStatus.SHIPPING));
        assertFalse(OrderLifecycle.canRequestReturn(OrderStatus.RETURN_REQUESTED));
        assertFalse(OrderLifecycle.canRequestReturn(OrderStatus.CANCELLED));
    }

    @Test
    void cancelRulesUnchanged() {
        assertTrue(OrderLifecycle.canUserCancel(OrderStatus.ORDERED));
        assertTrue(OrderLifecycle.canUserCancel(OrderStatus.PAID));
        assertFalse(OrderLifecycle.canUserCancel(OrderStatus.DELIVERED));
        assertFalse(OrderLifecycle.canUserCancel(OrderStatus.RETURN_REQUESTED));
    }
}
