package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.ProductImage;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductImageRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceDetailImageTest {

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
    void createKeepsRepresentativeImageSeparateFromDetailImages() {
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(3L);
            return product;
        });
        when(productImageRepository.save(any())).thenAnswer(invocation -> {
            ProductImage image = invocation.getArgument(0);
            image.setId(11L);
            return image;
        });

        Product created = service().createProduct(Map.of(
                "name", "머그컵",
                "price", new BigDecimal("12000"),
                "imageUrl", "https://example.com/cover.png",
                "detailImages", List.of(image(null, "https://example.com/detail.png"))
        ));

        assertEquals("https://example.com/cover.png", created.getImageUrl());
        assertEquals(1, created.getDetailImages().size());
        assertEquals("https://example.com/detail.png", created.getDetailImages().get(0).getImageUrl());
        assertEquals(0, created.getDetailImages().get(0).getSortOrder());
        verify(fileStorageService, never()).deleteIfStored(any());
    }

    @Test
    void getProductKeepsRepresentativeAndDetailImagesSeparate() {
        Product product = existingProduct();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L))
                .thenReturn(List.of(detail(1L, "https://example.com/a.png", 0), detail(2L, "https://example.com/b.png", 1)));

        Product found = service().getProduct(3L);

        assertEquals("https://example.com/cover.png", found.getImageUrl());
        assertEquals(List.of(1L, 2L), found.getDetailImages().stream().map(ProductImage::getId).toList());
    }

    @Test
    void createStoresTenDetailImagesInRequestOrder() {
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(3L);
            return product;
        });
        when(productImageRepository.save(any())).thenAnswer(invocation -> {
            ProductImage image = invocation.getArgument(0);
            image.setId(image.getSortOrder().longValue() + 1);
            return image;
        });
        List<Map<String, Object>> images = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            images.add(image(null, "https://example.com/detail-" + index + ".png"));
        }

        Product created = service().createProduct(Map.of(
                "name", "머그컵",
                "price", new BigDecimal("12000"),
                "imageUrl", "https://example.com/cover.png",
                "detailImages", images
        ));

        assertEquals("https://example.com/cover.png", created.getImageUrl());
        assertEquals(10, created.getDetailImages().size());
        assertEquals("https://example.com/detail-0.png", created.getDetailImages().get(0).getImageUrl());
        assertEquals("https://example.com/detail-9.png", created.getDetailImages().get(9).getImageUrl());
        assertEquals(9, created.getDetailImages().get(9).getSortOrder());
    }

    @Test
    void createAllowsProductWithoutDetailImages() {
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = service().createProduct(Map.of(
                "name", "머그컵",
                "price", new BigDecimal("12000"),
                "imageUrl", "https://example.com/cover.png"
        ));

        assertEquals("https://example.com/cover.png", created.getImageUrl());
        assertTrue(created.getDetailImages().isEmpty());
        verify(productImageRepository, never()).save(any());
    }

    @Test
    void createRejectsElevenDetailImages() {
        List<Map<String, Object>> images = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            images.add(image(null, "https://example.com/" + index + ".png"));
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().createProduct(Map.of(
                        "name", "머그컵",
                        "price", new BigDecimal("12000"),
                        "imageUrl", "https://example.com/cover.png",
                        "detailImages", images
                )));

        assertEquals("상세 이미지는 최대 10장까지 등록할 수 있습니다.", error.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateKeepsDetailImagesWhenFieldIsOmittedAndRepresentativeChanges() {
        Product product = existingProduct();
        ProductImage detail = detail(8L, "https://example.com/detail.png", 0);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(detail));

        Product updated = service().updateProduct(3L, Map.of("imageUrl", "https://example.com/new-cover.png"));

        assertEquals("https://example.com/new-cover.png", updated.getImageUrl());
        assertEquals(List.of(detail), updated.getDetailImages());
        verify(productImageRepository, never()).deleteAll(any());
        verify(fileStorageService, never()).deleteIfStored(any());
    }

    @Test
    void updateKeepsRepresentativeImageWhenOnlyDetailImagesChange() {
        Product product = existingProduct();
        ProductImage kept = detail(8L, "https://example.com/old-a.png", 0);
        ProductImage removed = detail(9L, "https://example.com/old-b.png", 1);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(kept, removed));
        when(productImageRepository.save(any())).thenAnswer(invocation -> {
            ProductImage image = invocation.getArgument(0);
            image.setId(12L);
            return image;
        });

        Product updated = service().updateProduct(3L, Map.of(
                "detailImages", List.of(
                        image(8L, "https://example.com/old-a.png"),
                        image(null, "https://example.com/new.png")
                )
        ));

        assertEquals("https://example.com/cover.png", updated.getImageUrl());
        assertEquals(0, kept.getSortOrder());
        assertEquals(2, updated.getDetailImages().size());
        assertEquals("https://example.com/new.png", updated.getDetailImages().get(1).getImageUrl());
        assertEquals(1, updated.getDetailImages().get(1).getSortOrder());
        verify(productImageRepository).deleteAll(List.of(removed));
        verify(fileStorageService, never()).deleteIfStored(any());
    }

    @Test
    void updateReordersExistingDetailImages() {
        Product product = existingProduct();
        ProductImage first = detail(8L, "https://example.com/a.png", 0);
        ProductImage second = detail(9L, "https://example.com/b.png", 1);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(first, second));

        Product updated = service().updateProduct(3L, Map.of(
                "detailImages", List.of(image(9L, "https://example.com/b.png"), image(8L, "https://example.com/a.png"))
        ));

        assertEquals(List.of(9L, 8L), updated.getDetailImages().stream().map(ProductImage::getId).toList());
        assertEquals(0, second.getSortOrder());
        assertEquals(1, first.getSortOrder());
        verify(productImageRepository, never()).deleteAll(any());
    }

    @Test
    void updateClearsDetailImagesOnlyWhenListIsExplicitlyEmpty() {
        Product product = existingProduct();
        ProductImage detail = detail(8L, "https://example.com/detail.png", 0);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(detail));

        Product updated = service().updateProduct(3L, Map.of("detailImages", List.of()));

        assertEquals("https://example.com/cover.png", updated.getImageUrl());
        assertTrue(updated.getDetailImages().isEmpty());
        verify(productImageRepository).deleteAll(List.of(detail));
        verify(fileStorageService, never()).deleteIfStored(any());
    }

    @Test
    void updateRejectsDetailImageOwnedByAnotherProduct() {
        Product product = existingProduct();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateProduct(3L, Map.of("detailImages", List.of(image(99L, "https://example.com/other.png"))))
        );

        assertEquals("해당 상품의 상세 이미지가 아닙니다.", error.getMessage());
        verify(productImageRepository, never()).deleteAll(any());
        verify(fileStorageService, never()).deleteIfStored(any());
    }

    @Test
    void deleteRemovesRepresentativeAndDetailFilesWithExistingRules() {
        Product product = existingProduct();
        ProductImage ours = detail(8L, "https://cdn.example/storage/detail.png", 0);
        ProductImage external = detail(9L, "https://picsum.photos/seed/a/800", 1);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProduct_Id(3L)).thenReturn(false);
        when(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(3L)).thenReturn(List.of(ours, external));

        service().deleteProduct(3L);

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService, org.mockito.Mockito.times(3)).deleteIfStored(urls.capture());
        assertEquals(List.of(
                "https://example.com/cover.png",
                "https://cdn.example/storage/detail.png",
                "https://picsum.photos/seed/a/800"
        ), urls.getAllValues());
        verify(productImageRepository).deleteAll(List.of(ours, external));
        verify(productRepository).delete(product);
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

    private Product existingProduct() {
        Product product = new Product();
        product.setId(3L);
        product.setName("머그컵");
        product.setPrice(new BigDecimal("12000"));
        product.setImageUrl("https://example.com/cover.png");
        return product;
    }

    private ProductImage detail(Long id, String imageUrl, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setImageUrl(imageUrl);
        image.setSortOrder(sortOrder);
        return image;
    }

    private Map<String, Object> image(Long id, String imageUrl) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("imageUrl", imageUrl);
        return values;
    }
}
