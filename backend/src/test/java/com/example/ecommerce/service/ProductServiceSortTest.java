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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceSortTest {

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
    @Mock
    private ProductCatalogQuery productCatalogQuery;

    @Test
    void salesSortKeepsTheAggregatedProductOrder() {
        when(productCatalogQuery.parseSort("sales")).thenReturn(ProductCatalogQuery.SortKey.SALES);
        when(productCatalogQuery.find(null, null, ProductCatalogQuery.SortKey.SALES, 0, 12))
                .thenReturn(new ProductCatalogQuery.CatalogPage(List.of(9L, 2L), 2));
        when(productRepository.findAllById(List.of(9L, 2L))).thenReturn(List.of(product(2L), product(9L)));

        @SuppressWarnings("unchecked")
        Page<Product> page = (Page<Product>) service().getProducts(0, 12, null, null, "sales");

        assertEquals(List.of(9L, 2L), page.getContent().stream().map(Product::getId).toList());
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void sortFailureFallsBackToNewestProducts() {
        when(productCatalogQuery.parseSort("likes")).thenReturn(ProductCatalogQuery.SortKey.LIKES);
        when(productCatalogQuery.find(null, null, ProductCatalogQuery.SortKey.LIKES, 0, 12))
                .thenThrow(new DataAccessResourceFailureException("집계 실패"));
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product(4L))));

        @SuppressWarnings("unchecked")
        Page<Product> page = (Page<Product>) service().getProducts(0, 12, null, null, "likes");

        assertEquals(4L, page.getContent().get(0).getId());
    }

    private ProductService service() {
        return new ProductService(
                productRepository,
                fileStorageService,
                orderItemRepository,
                cartItemRepository,
                productImageRepository,
                categoryRepository,
                productCatalogQuery);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("상품 " + id);
        product.setPrice(new BigDecimal("1000"));
        return product;
    }
}
