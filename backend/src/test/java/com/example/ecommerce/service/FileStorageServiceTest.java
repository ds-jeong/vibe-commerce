package com.example.ecommerce.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    private HttpServer server;
    private Path uploadRoot;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        if (uploadRoot != null) {
            Files.walk(uploadRoot)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // temp cleanup
                        }
                    });
        }
    }

    @Test
    void storeFailsWhenSupabaseSettingsAreMissing() throws IOException {
        FileStorageService service = service("", "", "");
        MockMultipartFile file = image();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.store(file));

        assertTrue(error.getMessage().contains("SUPABASE_URL"));
        assertEquals(0, Files.list(uploadRoot).count());
    }

    @Test
    void storeUploadsToSupabaseAndReturnsPublicUrl() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = "{\"Key\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        FileStorageService service = service(baseUrl(), "product-images", "service-role");
        String imageUrl = service.store(image());

        assertEquals("POST", method.get());
        assertTrue(path.get().startsWith("/storage/v1/object/product-images/"));
        assertTrue(path.get().endsWith(".jpg"));
        assertEquals("Bearer service-role", authorization.get());
        assertEquals("service-role", apiKey.get());
        assertEquals("image/jpeg", contentType.get());
        assertTrue(imageUrl.startsWith(baseUrl() + "/storage/v1/object/public/product-images/"));
        assertTrue(imageUrl.endsWith(".jpg"));
        assertEquals(0, Files.list(uploadRoot).count());
    }

    @Test
    void deleteRemovesOnlyOurBucketObjectOrLegacyLocalFile() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        FileStorageService service = service(baseUrl(), "product-images", "service-role");
        Path legacy = uploadRoot.resolve("legacy.jpg");
        Files.writeString(legacy, "local");

        service.deleteIfStored("https://picsum.photos/seed/a/800");
        assertEquals(null, method.get());
        assertTrue(Files.exists(legacy));

        service.deleteIfStored(baseUrl() + "/storage/v1/object/public/product-images/abc.jpg");
        assertEquals("DELETE", method.get());
        assertEquals("/storage/v1/object/product-images/abc.jpg", path.get());
        assertTrue(Files.exists(legacy));

        service.deleteIfStored("/uploads/legacy.jpg");
        assertFalse(Files.exists(legacy));
    }

    private FileStorageService service(String url, String bucket, String key) throws IOException {
        uploadRoot = Files.createTempDirectory("uploads");
        return new FileStorageService(uploadRoot.toString(), url, bucket, key);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
