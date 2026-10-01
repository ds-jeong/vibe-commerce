package com.example.ecommerce.service;

import com.example.ecommerce.domain.CartItem;
import com.example.ecommerce.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;

    public CartService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartList(String username) {
        return cartItemRepository.findByUsername(username);
    }

    public Map<String, Object> addCartItem(String username, Map<String, Object> item) {
        Long productId = ((Number) item.get("id")).longValue();
        String productName = (String) item.get("name");
        Long price = ((Number) item.get("price")).longValue();
        int quantity = item.get("quantity") == null
                ? 1
                : ((Number) item.get("quantity")).intValue();

        List<CartItem> existingItems =
                cartItemRepository.findByUsernameAndProductId(username, productId);

        if (!existingItems.isEmpty()) {
            CartItem existingItem = existingItems.get(0);
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            cartItemRepository.save(existingItem);
        } else {
            CartItem cartItem = new CartItem(username, productId, productName, price, quantity);
            cartItemRepository.save(cartItem);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "장바구니에 상품을 담았습니다.");
        return response;
    }

    public Map<String, Object> mergeGuestCart(String username, List<Map<String, Object>> guestItems) {
        for (Map<String, Object> item : guestItems) {
            Long productId = ((Number) item.get("id")).longValue();
            String productName = (String) item.get("name");
            Long price = ((Number) item.get("price")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            CartItem cartItem = new CartItem(username, productId, productName, price, quantity);
            cartItemRepository.save(cartItem);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "독립 패키지 검증 통과 및 [" + username + "] 회원 장바구니 이관 마감");
        return response;
    }

    public void deleteByProductId(String username, Long productId) {
        int deletedCount = cartItemRepository.deleteByUsernameAndProductId(username, productId);

        if (deletedCount == 0) {
            throw new NoSuchElementException("장바구니에 해당 상품이 없습니다.");
        }
    }

    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }
}
