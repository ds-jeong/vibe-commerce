package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.SaleStatus;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductImageRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceCategoryTest {

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
    void listWithoutCategoryDoesNotFilterOrFillMissingCategories() {
        Product product = new Product();
        product.setId(1L);
        product.setName("기존상품");
        when(productRepository.findAll(any(Sort.class))).thenReturn(List.of(product));

        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) service().getProducts(null, null, null);

        assertNull(products.get(0).getCategoryId());
        assertNull(products.get(0).getCategoryName());
        verify(categoryRepository, never()).existsById(any());
        verify(productRepository, never()).findByCategory_Id(any(), any(Sort.class));
    }

    @Test
    void listByCategoryReturnsOnlyThatCategory() {
        Product product = productIn(category());
        when(categoryRepository.existsById(3L)).thenReturn(true);
        when(productRepository.findByCategory_Id(eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        @SuppressWarnings("unchecked")
        Page<Product> page = (Page<Product>) service().getProducts(0, 12, " ", 3L);

        assertEquals(1, page.getContent().size());
        assertEquals(3L, page.getContent().get(0).getCategoryId());
        assertEquals("상의", page.getContent().get(0).getCategoryName());
    }

    @Test
    void unknownCategoryIsRejectedBeforeQuery() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().getProducts(null, null, null, 99L));

        assertEquals("카테고리를 찾을 수 없습니다.", error.getMessage());
        verify(productRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void createAssignsExistingCategoryWithoutChangingImage() {
        Category category = category();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = service().createProduct(Map.of(
                "name", "테스트상품",
                "price", new BigDecimal("9000"),
                "imageUrl", "https://example.com/cover.png",
                "categoryId", 3
        ));

        assertEquals(category, created.getCategory());
        assertEquals(3L, created.getCategoryId());
        assertEquals("상의", created.getCategoryName());
        assertEquals("https://example.com/cover.png", created.getImageUrl());
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "테스트상품");
        payload.put("price", 9000);
        payload.put("categoryId", 99);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().createProduct(payload));

        assertEquals("카테고리를 찾을 수 없습니다.", error.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateOmitsCategoryWhenKeyIsAbsentAndClearsItWhenNull() {
        Category category = category();
        Product product = productIn(category);
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(4L)).thenReturn(List.of());

        Product kept = service().updateProduct(4L, Map.of("price", 2000));
        assertEquals(3L, kept.getCategoryId());
        assertEquals("https://example.com/cover.png", kept.getImageUrl());

        Map<String, Object> clear = new HashMap<>();
        clear.put("categoryId", null);
        Product cleared = service().updateProduct(4L, clear);
        assertNull(cleared.getCategory());
        assertNull(cleared.getCategoryId());
    }

    private Product productIn(Category category) {
        Product product = new Product();
        product.setId(4L);
        product.setName("테스트상품");
        product.setPrice(new BigDecimal("9000"));
        product.setImageUrl("https://example.com/cover.png");
        product.setCategory(category);
        return product;
    }

    private Category category() {
        Category category = new Category();
        category.setId(3L);
        category.setName("상의");
        return category;
    }

    @Test
    void createWithoutSaleStatusDefaultsToOnSale() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = service().createProduct(Map.of("name", "새상품", "price", 1000));

        assertEquals(SaleStatus.ON_SALE, saved.getSaleStatus());
    }

    @Test
    void updateKeepsSaleStatusWhenOmittedAndAppliesStoppedWhenSent() {
        Product product = new Product();
        product.setId(4L);
        product.setSaleStatus(null);
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product kept = service().updateProduct(4L, Map.of("name", "유지"));
        assertNull(kept.getSaleStatus());

        product.setSaleStatus(SaleStatus.ON_SALE);
        Product stopped = service().updateProduct(4L, Map.of("saleStatus", "STOPPED"));
        assertEquals(SaleStatus.STOPPED, stopped.getSaleStatus());
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
}
