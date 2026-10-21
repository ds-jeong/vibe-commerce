package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    @Test
    void createRequiresNameAndRejectsDuplicate() {
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> service().createCategory(Map.of("name", "  ")));
        assertEquals("카테고리명은 필수입니다.", missing.getMessage());

        Category existing = new Category();
        existing.setId(1L);
        existing.setName("상의");
        when(categoryRepository.findByName("상의")).thenReturn(Optional.of(existing));

        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> service().createCategory(Map.of("name", "상의")));
        assertEquals("이미 있는 카테고리명입니다.", duplicate.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createStoresActiveCategory() {
        when(categoryRepository.findByName("하의")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(8L);
            return category;
        });

        Category created = service().createCategory(Map.of("name", " 하의 ", "description", "바지"));

        assertEquals(8L, created.getId());
        assertEquals("하의", created.getName());
        assertEquals("바지", created.getDescription());
        assertTrue(created.isActive());
    }

    @Test
    void deleteRejectsCategoryThatStillHasProducts() {
        Category category = new Category();
        category.setId(8L);
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategory_Id(8L)).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().deleteCategory(8L));

        assertEquals("상품이 연결된 카테고리는 삭제할 수 없습니다.", error.getMessage());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void updateRenamesCategoryWithoutChangingId() {
        Category category = new Category();
        category.setId(8L);
        category.setName("상의");
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByName("아우터")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = service().updateCategory(8L, Map.of("name", " 아우터 "));

        assertEquals(8L, updated.getId());
        assertEquals("아우터", updated.getName());
    }

    @Test
    void updateRejectsBlankAndDuplicateNames() {
        Category category = new Category();
        category.setId(8L);
        category.setName("상의");
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));

        IllegalArgumentException blank = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateCategory(8L, Map.of("name", " ")));
        assertEquals("카테고리명은 필수입니다.", blank.getMessage());

        Category other = new Category();
        other.setId(3L);
        other.setName("하의");
        when(categoryRepository.findByName("하의")).thenReturn(Optional.of(other));
        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateCategory(8L, Map.of("name", "하의")));
        assertEquals("이미 있는 카테고리명입니다.", duplicate.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void adminListIncludesProductCountWithoutExtraColumns() {
        Category category = new Category();
        category.setId(8L);
        category.setName("상의");
        category.setActive(true);
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(category));
        when(productRepository.countGroupedByCategoryId()).thenReturn(List.<Object[]>of(new Object[]{8L, 2L}));

        List<Map<String, Object>> rows = service().getAdminCategories();

        assertEquals(1, rows.size());
        assertEquals(8L, rows.get(0).get("id"));
        assertEquals("상의", rows.get(0).get("name"));
        assertEquals(2L, rows.get(0).get("productCount"));
    }

    private CategoryService service() {
        return new CategoryService(categoryRepository, productRepository);
    }
}
