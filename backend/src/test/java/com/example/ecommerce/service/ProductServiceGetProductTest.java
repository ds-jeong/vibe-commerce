package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductImageRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceGetProductTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void getProductReturnsExistingProduct() {
        Product product = new Product();
        product.setId(7L);
        product.setName("머그컵");
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));

        Product found = service().getProduct(7L);

        assertEquals(7L, found.getId());
        assertEquals("머그컵", found.getName());
    }

    @Test
    void getProductRejectsMissingProduct() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service().getProduct(99L));

        assertEquals("상품을 찾을 수 없습니다.", error.getMessage());
    }

    private ProductService service() {
        return new ProductService(
                productRepository,
                fileStorageService,
                orderItemRepository,
                cartItemRepository,
                productImageRepository,
                categoryRepository);
    }
}
