package com.example.ecommerce.it;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.Role;
import com.example.ecommerce.domain.User;
import com.example.ecommerce.global.JwtProvider;
import com.example.ecommerce.repository.ProductLikeRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.ProductLikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("itest")
class ProductLikeApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductLikeRepository productLikeRepository;
    @Autowired
    private ProductLikeService productLikeService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void likesRequireTheAuthenticatedUserAndStayUnique() throws Exception {
        User member = saveUser("likea");
        User other = saveUser("likeb");
        Product product = saveProduct();
        String memberToken = jwtProvider.createToken(member.getUserKey(), "USER");
        String otherToken = jwtProvider.createToken(other.getUserKey(), "USER");
        try {
            mockMvc.perform(post("/api/products/" + product.getId() + "/likes"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/products/" + product.getId() + "/likes"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/products/999999/likes")
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다."));

            mockMvc.perform(post("/api/products/" + product.getId() + "/likes")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType("application/json")
                            .content("{\"userId\":" + other.getId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(1))
                    .andExpect(jsonPath("$.likedByCurrentUser").value(true));
            mockMvc.perform(post("/api/products/" + product.getId() + "/likes")
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(1));

            mockMvc.perform(delete("/api/products/" + product.getId() + "/likes")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likedByCurrentUser").value(false))
                    .andExpect(jsonPath("$.likeCount").value(1));
            assertEquals(1L, productLikeRepository.countByProduct_Id(product.getId()));

            mockMvc.perform(get("/api/products/" + product.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").doesNotExist());
            mockMvc.perform(get("/api/products").param("keyword", product.getName()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value(product.getName()))
                    .andExpect(jsonPath("$[0].likeCount").doesNotExist());
            mockMvc.perform(get("/api/products").param("keyword", product.getName())
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].likeCount").value(1))
                    .andExpect(jsonPath("$[0].likedByCurrentUser").value(true))
                    .andExpect(jsonPath("$[0].price").exists());

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            AtomicInteger successes = new AtomicInteger();
            try {
                Future<?> first = pool.submit(() -> likeTogether(other.getUserKey(), product.getId(), start, successes));
                Future<?> second = pool.submit(() -> likeTogether(other.getUserKey(), product.getId(), start, successes));
                start.countDown();
                first.get(8, TimeUnit.SECONDS);
                second.get(8, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            assertEquals(2, successes.get());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "select count(*) from product_likes where user_id = ? and product_id = ?",
                    Long.class, other.getId(), product.getId()));
            assertEquals(2L, productLikeRepository.countByProduct_Id(product.getId()));
        } finally {
            jdbcTemplate.update("delete from product_likes where product_id = ?", product.getId());
            jdbcTemplate.update("delete from products where id = ?", product.getId());
            jdbcTemplate.update("delete from users where id in (?, ?)", member.getId(), other.getId());
        }
    }

    private void likeTogether(String userKey, Long productId, CountDownLatch start, AtomicInteger successes) {
        try {
            start.await(5, TimeUnit.SECONDS);
            productLikeService.like(userKey, productId);
            successes.incrementAndGet();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User saveUser(String prefix) {
        User user = new User();
        user.setUserKey(prefix + UUID.randomUUID().toString().substring(0, 4));
        user.setPassword("x");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private Product saveProduct() {
        Product product = new Product();
        product.setName("it-like-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("1000"));
        product.setStockQuantity(3);
        return productRepository.save(product);
    }
}
