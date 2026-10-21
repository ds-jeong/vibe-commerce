package com.example.ecommerce.controller;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ProductImage;
import com.example.ecommerce.service.ProductLikeService;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerGetProductTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductLikeService productLikeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService, productLikeService)).build();
    }

    @Test
    void getProductReturnsExistingFields() throws Exception {
        Product product = new Product();
        product.setId(7L);
        product.setName("머그컵");
        product.setPrice(new BigDecimal("12000"));
        product.setStockQuantity(3);
        product.setDescription("따뜻한 음료용");
        product.setImageUrl("https://example.com/mug.png");
        ProductImage detailImage = new ProductImage();
        detailImage.setId(101L);
        detailImage.setImageUrl("https://example.com/detail.png");
        detailImage.setSortOrder(0);
        product.setDetailImages(List.of(detailImage));
        when(productService.getProduct(7L)).thenReturn(product);

        mockMvc.perform(get("/api/products/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("머그컵"))
                .andExpect(jsonPath("$.stockQuantity").value(3))
                .andExpect(jsonPath("$.description").value("따뜻한 음료용"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/mug.png"))
                .andExpect(jsonPath("$.detailImages[0].id").value(101))
                .andExpect(jsonPath("$.detailImages[0].imageUrl").value("https://example.com/detail.png"))
                .andExpect(jsonPath("$.detailImages[0].sortOrder").value(0))
                .andExpect(jsonPath("$.detailImages[0].product").doesNotExist());
    }

    @Test
    void getProductReturnsFailWhenMissing() throws Exception {
        when(productService.getProduct(99L)).thenThrow(new IllegalArgumentException("상품을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다."));
    }

    @Test
    void productListEndpointStaysAvailable() throws Exception {
        Product product = new Product();
        product.setId(7L);
        product.setName("머그컵");
        product.setPrice(new BigDecimal("12000"));
        product.setImageUrl("https://example.com/mug.png");
        when(productService.getProducts(null, null, null, null)).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value("https://example.com/mug.png"))
                .andExpect(jsonPath("$[0].detailImages").doesNotExist())
                .andExpect(jsonPath("$[0].categoryId").doesNotExist());
    }

    @Test
    void unknownCategoryFilterReturnsFail() throws Exception {
        when(productService.getProducts(null, null, null, 99L))
                .thenThrow(new IllegalArgumentException("카테고리를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/products").param("categoryId", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.message").value("카테고리를 찾을 수 없습니다."));
    }
}
