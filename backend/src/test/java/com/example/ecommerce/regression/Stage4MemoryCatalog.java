package com.example.ecommerce.regression;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ReturnRequest;
import com.example.ecommerce.domain.ReturnStatus;
import com.example.ecommerce.domain.Settlement;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.repository.OrdersRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReturnRequestRepository;
import com.example.ecommerce.repository.SettlementRepository;
import com.example.ecommerce.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * In-memory stubs for stage-4 service regression. Does not touch PostgreSQL or PortOne.
 */
final class Stage4MemoryCatalog {

    final Map<Long, Orders> orders = new HashMap<>();
    final Map<String, Orders> ordersByMerchant = new HashMap<>();
    final Map<Long, ReturnRequest> returns = new HashMap<>();
    final Map<Long, Product> products = new HashMap<>();
    final Map<Long, User> users = new HashMap<>();
    final Map<String, User> usersByKey = new HashMap<>();
    final Map<Long, Payment> payments = new HashMap<>();
    final Map<Long, Settlement> settlements = new HashMap<>();

    private final AtomicLong orderIds = new AtomicLong(1);
    private final AtomicLong returnIds = new AtomicLong(1);
    private final AtomicLong paymentIds = new AtomicLong(1);
    private final AtomicLong settlementIds = new AtomicLong(1);

    void stub(
            OrdersRepository ordersRepository,
            ReturnRequestRepository returnRequestRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            SettlementRepository settlementRepository) {
        lenient().when(ordersRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders order = inv.getArgument(0);
            if (order.getId() == null) {
                order.setId(orderIds.getAndIncrement());
            }
            orders.put(order.getId(), order);
            if (order.getOrderMerchantUid() != null) {
                ordersByMerchant.put(order.getOrderMerchantUid(), order);
            }
            return order;
        });
        lenient().when(ordersRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(orders.get(inv.getArgument(0))));
        lenient().when(ordersRepository.findWithItemsById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(orders.get(inv.getArgument(0))));
        lenient().when(ordersRepository.findWithItemsByOrderMerchantUid(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(ordersByMerchant.get(inv.getArgument(0))));
        lenient().when(ordersRepository.findByOrderMerchantUid(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(ordersByMerchant.get(inv.getArgument(0))));
        lenient().when(ordersRepository.findAllWithItems())
                .thenAnswer(inv -> new ArrayList<>(orders.values()));
        lenient().when(ordersRepository.findWithItemsByUser(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return orders.values().stream()
                    .filter(order -> order.getUser() != null && user.getId().equals(order.getUser().getId()))
                    .toList();
        });
        lenient().when(ordersRepository.findLegacyReturnRequestedWithoutReturnRequest(any(OrderStatus.class)))
                .thenAnswer(inv -> {
                    OrderStatus status = inv.getArgument(0);
                    return orders.values().stream()
                            .filter(order -> order.getStatus() == status)
                            .filter(order -> returns.values().stream()
                                    .noneMatch(request -> request.getOrder() != null
                                            && order.getId().equals(request.getOrder().getId())))
                            .toList();
                });

        lenient().when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest request = inv.getArgument(0);
            if (request.getId() == null) {
                request.setId(returnIds.getAndIncrement());
            }
            returns.put(request.getId(), request);
            return request;
        });
        lenient().when(returnRequestRepository.existsByOrder_IdAndStatusIn(anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Long orderId = inv.getArgument(0);
                    Collection<ReturnStatus> statuses = inv.getArgument(1);
                    return returns.values().stream().anyMatch(request ->
                            request.getOrder() != null
                                    && orderId.equals(request.getOrder().getId())
                                    && statuses.contains(request.getStatus()));
                });
        lenient().when(returnRequestRepository.existsByOrder_Id(anyLong()))
                .thenAnswer(inv -> {
                    Long orderId = inv.getArgument(0);
                    return returns.values().stream().anyMatch(request ->
                            request.getOrder() != null && orderId.equals(request.getOrder().getId()));
                });
        lenient().when(returnRequestRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Long orderId = inv.getArgument(0);
                    Collection<ReturnStatus> statuses = inv.getArgument(1);
                    return returns.values().stream()
                            .filter(request -> request.getOrder() != null
                                    && orderId.equals(request.getOrder().getId())
                                    && statuses.contains(request.getStatus()))
                            .max(Comparator.comparing(ReturnRequest::getId));
                });
        lenient().when(returnRequestRepository.updateStatusIfMatch(anyLong(), any(ReturnStatus.class), any(ReturnStatus.class)))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    ReturnStatus expected = inv.getArgument(1);
                    ReturnStatus next = inv.getArgument(2);
                    ReturnRequest request = returns.get(id);
                    if (request != null && request.getStatus() == expected) {
                        request.setStatus(next);
                        return 1;
                    }
                    return 0;
                });
        lenient().when(returnRequestRepository.findAllWithOrderItems())
                .thenAnswer(inv -> new ArrayList<>(returns.values()));

        lenient().when(productRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(products.get(inv.getArgument(0))));
        lenient().when(productRepository.decrementStockIfEnough(anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    Product product = products.get(inv.getArgument(0));
                    if (product == null) {
                        return 0;
                    }
                    int count = inv.getArgument(1);
                    int current = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
                    if (current < count) {
                        return 0;
                    }
                    product.setStockQuantity(current - count);
                    return 1;
                });
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            products.put(product.getId(), product);
            return product;
        });

        lenient().when(userRepository.findByUserKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(usersByKey.get(inv.getArgument(0))));

        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(paymentIds.getAndIncrement());
            }
            payments.put(payment.getId(), payment);
            return payment;
        });
        lenient().when(paymentRepository.findByOrder(any(Orders.class))).thenAnswer(inv -> {
            Orders order = inv.getArgument(0);
            if (order == null || order.getId() == null) {
                return Optional.empty();
            }
            return payments.values().stream()
                    .filter(payment -> payment.getOrder() != null
                            && order.getId().equals(payment.getOrder().getId()))
                    .findFirst();
        });

        lenient().when(settlementRepository.findByOrder_Id(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(settlements.get(inv.getArgument(0))));
        lenient().when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement settlement = inv.getArgument(0);
            if (settlement.getId() == null) {
                settlement.setId(settlementIds.getAndIncrement());
            }
            if (settlement.getOrder() != null && settlement.getOrder().getId() != null) {
                settlements.put(settlement.getOrder().getId(), settlement);
            }
            return settlement;
        });
    }

    User addUser(long id, String userKey) {
        User user = new User();
        user.setId(id);
        user.setUserKey(userKey);
        users.put(id, user);
        usersByKey.put(userKey, user);
        return user;
    }

    Product addProduct(long id, String name, int stock, java.math.BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setStockQuantity(stock);
        product.setPrice(price);
        products.put(id, product);
        return product;
    }
}
