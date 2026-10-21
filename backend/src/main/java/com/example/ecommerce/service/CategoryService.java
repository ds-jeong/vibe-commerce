package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAdminCategories() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : productRepository.countGroupedByCategoryId()) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Category category : categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.getId());
            row.put("name", category.getName());
            row.put("description", category.getDescription());
            row.put("active", category.isActive());
            row.put("createdAt", category.getCreatedAt());
            row.put("updatedAt", category.getUpdatedAt());
            row.put("productCount", counts.getOrDefault(category.getId(), 0L));
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
    }

    @Transactional
    public Category createCategory(Map<String, Object> payload) {
        Category category = new Category();
        apply(category, payload, true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, Map<String, Object> payload) {
        Category category = getCategory(id);
        apply(category, payload, true);
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategory(id);
        if (productRepository.existsByCategory_Id(id)) {
            throw new IllegalArgumentException("상품이 연결된 카테고리는 삭제할 수 없습니다.");
        }
        categoryRepository.delete(category);
    }

    private void apply(Category category, Map<String, Object> payload, boolean required) {
        if (payload == null) {
            throw new IllegalArgumentException("카테고리 정보가 없습니다.");
        }
        Object rawName = payload.get("name");
        String name = rawName == null ? "" : String.valueOf(rawName).trim();
        if (required && name.isBlank()) {
            throw new IllegalArgumentException("카테고리명은 필수입니다.");
        }
        if (!name.isBlank()) {
            if (name.length() > 50) {
                throw new IllegalArgumentException("카테고리명은 50자 이하로 입력해 주세요.");
            }
            categoryRepository.findByName(name).ifPresent(existing -> {
                if (category.getId() == null || !category.getId().equals(existing.getId())) {
                    throw new IllegalArgumentException("이미 있는 카테고리명입니다.");
                }
            });
            category.setName(name);
        }
        if (payload.containsKey("description")) {
            Object description = payload.get("description");
            category.setDescription(description == null ? null : String.valueOf(description));
        }
        if (payload.containsKey("active") && payload.get("active") != null) {
            category.setActive(Boolean.parseBoolean(String.valueOf(payload.get("active"))));
        }
    }
}
