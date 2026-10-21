package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.SaleStatus;
import com.example.ecommerce.domain.ProductImage;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductImageRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int MAX_DETAIL_IMAGES = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final FileStorageService fileStorageService;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCatalogQuery productCatalogQuery;

    public ProductService(
            ProductRepository productRepository,
            FileStorageService fileStorageService,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            ProductCatalogQuery productCatalogQuery) {
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.productCatalogQuery = productCatalogQuery;
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.setDetailImages(loadDetailImages(id));
        presentCategory(product);
        return product;
    }

    @Transactional(readOnly = true)
    public Object getProducts(Integer page, Integer size, String keyword) {
        return getProducts(page, size, keyword, null);
    }

    @Transactional(readOnly = true)
    public Object getProducts(Integer page, Integer size, String keyword, Long categoryId) {
        return getProducts(page, size, keyword, categoryId, null);
    }

    @Transactional(readOnly = true)
    public Object getProducts(Integer page, Integer size, String keyword, Long categoryId, String sort) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다.");
        }
        ProductCatalogQuery.SortKey sortKey = productCatalogQuery.parseSort(sort);
        if (sortKey != null) {
            try {
                return listByCatalog(page, size, keyword, categoryId, sortKey);
            } catch (DataAccessException ex) {
                log.warn("상품 정렬 집계에 실패해 최신순으로 조회합니다.");
            }
        }
        String trimmed = keyword == null ? "" : keyword.trim();
        Sort sortOrder = Sort.by(Sort.Direction.DESC, "id");
        boolean paged = page != null && size != null && page >= 0 && size > 0;
        Object result;

        if (paged) {
            Pageable pageable = PageRequest.of(page, size, sortOrder);
            if (categoryId != null && trimmed.isEmpty()) {
                result = productRepository.findByCategory_Id(categoryId, pageable);
            } else if (categoryId != null) {
                result = productRepository.findByCategory_IdAndNameContainingIgnoreCase(categoryId, trimmed, pageable);
            } else if (trimmed.isEmpty()) {
                result = productRepository.findAll(pageable);
            } else {
                result = productRepository.findByNameContainingIgnoreCase(trimmed, pageable);
            }
        } else if (categoryId != null && trimmed.isEmpty()) {
            result = productRepository.findByCategory_Id(categoryId, sortOrder);
        } else if (categoryId != null) {
            result = productRepository.findByCategory_IdAndNameContainingIgnoreCase(categoryId, trimmed, sortOrder);
        } else if (trimmed.isEmpty()) {
            result = productRepository.findAll(sortOrder);
        } else {
            result = productRepository.findByNameContainingIgnoreCase(trimmed, sortOrder);
        }
        presentCategories(result);
        return result;
    }

    private Object listByCatalog(
            Integer page,
            Integer size,
            String keyword,
            Long categoryId,
            ProductCatalogQuery.SortKey sortKey) {
        ProductCatalogQuery.CatalogPage catalogPage = productCatalogQuery.find(keyword, categoryId, sortKey, page, size);
        List<Product> ordered = orderByIds(catalogPage.ids());
        presentCategories(ordered);
        boolean paged = page != null && size != null && page >= 0 && size > 0;
        if (!paged) {
            return ordered;
        }
        return new PageImpl<>(ordered, PageRequest.of(page, size), catalogPage.total());
    }

    private List<Product> orderByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = new LinkedHashMap<>();
        for (Product product : productRepository.findAllById(ids)) {
            byId.put(product.getId(), product);
        }
        List<Product> ordered = new ArrayList<>();
        for (Long id : ids) {
            Product product = byId.get(id);
            if (product != null) {
                ordered.add(product);
            }
        }
        return ordered;
    }

    @Transactional
    public Product createProduct(Map<String, Object> payload) {
        Product product = new Product();
        applyProductFields(product, payload, true);
        List<DetailImageCommand> commands = readDetailCommands(payload);
        product.setCreatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);
        saved.setDetailImages(commands == null ? List.of() : applyDetailCommands(saved, commands));
        presentCategory(saved);
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, Map<String, Object> payload) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        List<DetailImageCommand> commands = readDetailCommands(payload);
        applyProductFields(product, payload, false);
        Product saved = productRepository.save(product);
        saved.setDetailImages(commands == null ? loadDetailImages(saved.getId()) : applyDetailCommands(saved, commands));
        presentCategory(saved);
        return saved;
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new IllegalArgumentException("주문 내역이 있는 상품은 삭제할 수 없습니다.");
        }
        cartItemRepository.deleteByProductId(id);
        List<ProductImage> detailImages = loadDetailImages(id);
        fileStorageService.deleteIfStored(product.getImageUrl());
        for (ProductImage detailImage : detailImages) {
            fileStorageService.deleteIfStored(detailImage.getImageUrl());
        }
        if (!detailImages.isEmpty()) {
            productImageRepository.deleteAll(detailImages);
        }
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    private void applyProductFields(Product product, Map<String, Object> payload, boolean required) {
        if (payload == null) {
            throw new IllegalArgumentException("상품 정보가 없습니다.");
        }
        Object name = payload.get("name");
        Object price = payload.get("price");
        if (required && (name == null || String.valueOf(name).isBlank() || price == null)) {
            throw new IllegalArgumentException("상품명과 가격은 필수입니다.");
        }
        if (name != null && !String.valueOf(name).isBlank()) {
            product.setName(String.valueOf(name).trim());
        }
        if (price != null) {
            product.setPrice(new java.math.BigDecimal(String.valueOf(price)));
        }
        if (payload.get("stockQuantity") != null) {
            product.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
        }
        if (payload.containsKey("imageUrl")) {
            Object imageUrl = payload.get("imageUrl");
            product.setImageUrl(imageUrl == null ? null : String.valueOf(imageUrl));
        }
        if (payload.containsKey("description")) {
            Object description = payload.get("description");
            product.setDescription(description == null ? null : String.valueOf(description));
        }
        if (payload.containsKey("categoryId")) {
            applyCategory(product, payload.get("categoryId"));
        }
        if (payload.containsKey("saleStatus")) {
            product.setSaleStatus(parseSaleStatus(payload.get("saleStatus")));
        }
    }

    private SaleStatus parseSaleStatus(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return SaleStatus.ON_SALE;
        }
        try {
            return SaleStatus.valueOf(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("판매 상태가 올바르지 않습니다.");
        }
    }

    private void applyCategory(Product product, Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            product.setCategory(null);
            return;
        }
        long categoryId;
        try {
            categoryId = Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다.");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
        product.setCategory(category);
    }

    private void presentCategories(Object result) {
        if (result instanceof org.springframework.data.domain.Page<?> page) {
            page.getContent().forEach(this::presentIfProduct);
            return;
        }
        if (result instanceof List<?> products) {
            products.forEach(this::presentIfProduct);
        }
    }

    private void presentIfProduct(Object item) {
        if (item instanceof Product product) {
            presentCategory(product);
        }
    }

    private void presentCategory(Product product) {
        Category category = product.getCategory();
        if (category == null) {
            product.setCategoryId(null);
            product.setCategoryName(null);
            return;
        }
        product.setCategoryId(category.getId());
        product.setCategoryName(category.getName());
    }

    private List<ProductImage> loadDetailImages(Long productId) {
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId);
        return images == null ? List.of() : images;
    }

    private List<DetailImageCommand> readDetailCommands(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("detailImages") || payload.get("detailImages") == null) {
            return null;
        }
        Object raw = payload.get("detailImages");
        if (!(raw instanceof List<?> items)) {
            throw new IllegalArgumentException("상세 이미지 형식이 올바르지 않습니다.");
        }
        if (items.size() > MAX_DETAIL_IMAGES) {
            throw new IllegalArgumentException("상세 이미지는 최대 10장까지 등록할 수 있습니다.");
        }
        List<DetailImageCommand> commands = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Object item : items) {
            DetailImageCommand command = parseDetailCommand(item);
            if (command.id != null && !ids.add(command.id)) {
                throw new IllegalArgumentException("상세 이미지가 중복되었습니다.");
            }
            commands.add(command);
        }
        return commands;
    }

    private DetailImageCommand parseDetailCommand(Object item) {
        if (!(item instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("상세 이미지 형식이 올바르지 않습니다.");
        }
        Object rawUrl = values.get("imageUrl");
        String imageUrl = rawUrl == null ? "" : String.valueOf(rawUrl).trim();
        if (imageUrl.isBlank()) {
            throw new IllegalArgumentException("상세 이미지 주소가 없습니다.");
        }
        if (imageUrl.length() > 2048) {
            throw new IllegalArgumentException("상세 이미지 주소가 너무 깁니다.");
        }
        return new DetailImageCommand(longValue(values.get("id")), imageUrl);
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("상세 이미지 번호가 올바르지 않습니다.");
        }
    }

    private List<ProductImage> applyDetailCommands(Product product, List<DetailImageCommand> commands) {
        List<ProductImage> existing = loadDetailImages(product.getId());
        Map<Long, ProductImage> byId = new HashMap<>();
        for (ProductImage image : existing) {
            if (image.getId() != null) {
                byId.put(image.getId(), image);
            }
        }
        List<ProductImage> ordered = new ArrayList<>();
        Set<Long> retained = new HashSet<>();
        for (DetailImageCommand command : commands) {
            if (command.id != null && !byId.containsKey(command.id)) {
                throw new IllegalArgumentException("해당 상품의 상세 이미지가 아닙니다.");
            }
        }
        for (int index = 0; index < commands.size(); index++) {
            DetailImageCommand command = commands.get(index);
            if (command.id != null) {
                ProductImage current = byId.get(command.id);
                current.setImageUrl(command.imageUrl);
                current.setSortOrder(index);
                retained.add(command.id);
                ordered.add(current);
                continue;
            }
            ProductImage created = new ProductImage();
            created.setProduct(product);
            created.setImageUrl(command.imageUrl);
            created.setSortOrder(index);
            created.setCreatedAt(LocalDateTime.now());
            ordered.add(productImageRepository.save(created));
        }
        List<ProductImage> removed = new ArrayList<>();
        for (ProductImage image : existing) {
            if (image.getId() != null && !retained.contains(image.getId())) {
                removed.add(image);
            }
        }
        if (!removed.isEmpty()) {
            productImageRepository.deleteAll(removed);
        }
        return ordered;
    }

    private static final class DetailImageCommand {
        private final Long id;
        private final String imageUrl;

        private DetailImageCommand(Long id, String imageUrl) {
            this.id = id;
            this.imageUrl = imageUrl;
        }
    }
}
