package com.example.ecommerce.service;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ReturnRequestService returnRequestService;

    private User owner;
    private Orders order;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUserKey("testuser");

        order = new Orders();
        order.setId(10L);
        order.setUser(owner);
        order.setStatus(OrderStatus.DELIVERED);
    }

    @Test
    void submitMemberReturn_succeedsForDeliveredOwnedOrder() {
        when(userRepository.findByUserKey("testuser")).thenReturn(Optional.of(owner));
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Orders result = returnRequestService.submitMemberReturn("testuser", 10L);

        assertEquals(OrderStatus.RETURN_REQUESTED, result.getStatus());
        ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        assertEquals(ReturnStatus.REQUESTED, captor.getValue().getStatus());
        assertEquals(order, captor.getValue().getOrder());
    }

    @Test
    void submitMemberReturn_rejectsBeforeDelivered() {
        order.setStatus(OrderStatus.SHIPPING);
        when(userRepository.findByUserKey("testuser")).thenReturn(Optional.of(owner));
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> returnRequestService.submitMemberReturn("testuser", 10L));
        assertEquals("배송완료 주문만 반품 신청할 수 있습니다.", ex.getMessage());
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void submitMemberReturn_rejectsOtherUsersOrder() {
        User other = new User();
        other.setId(2L);
        order.setUser(other);
        when(userRepository.findByUserKey("testuser")).thenReturn(Optional.of(owner));
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.submitMemberReturn("testuser", 10L));
        assertEquals("본인 주문만 처리할 수 있습니다.", ex.getMessage());
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void submitMemberReturn_rejectsActiveDuplicate() {
        when(userRepository.findByUserKey("testuser")).thenReturn(Optional.of(owner));
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(10L), any(Set.class))).thenReturn(true);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> returnRequestService.submitMemberReturn("testuser", 10L));
        assertEquals("이미 진행 중인 반품 신청이 있습니다.", ex.getMessage());
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void submitMemberReturn_allowsRetryWhenOnlyRejectedExists() {
        when(userRepository.findByUserKey("testuser")).thenReturn(Optional.of(owner));
        when(ordersRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrder_IdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(ordersRepository.findWithItemsById(10L)).thenReturn(Optional.of(order));

        Orders result = returnRequestService.submitMemberReturn("testuser", 10L);

        assertEquals(OrderStatus.RETURN_REQUESTED, result.getStatus());
        verify(returnRequestRepository).save(any(ReturnRequest.class));
    }
}
