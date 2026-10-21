package com.example.ecommerce.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        String imageUrl = service.store(jpeg(800, 800));

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

    @Test
    void storeRejectsFileLargerThanTwoMegabytes() throws IOException {
        FileStorageService service = service("http://127.0.0.1:9", "product-images", "service-role");
        byte[] bytes = new byte[(2 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", bytes);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertTrue(error.getMessage().contains("2MB"));
    }

    @Test
    void storeRejectsImageSmallerThan800Pixels() throws IOException {
        FileStorageService service = service("http://127.0.0.1:9", "product-images", "service-role");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.store(jpeg(799, 800)));

        assertTrue(error.getMessage().contains("800"));
    }

    @Test
    void storeAcceptsWebpWhoseHeaderIsAtLeast800Pixels() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"Key\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        FileStorageService service = service(baseUrl(), "product-images", "service-role");
        String imageUrl = service.store(new MockMultipartFile("file", "photo.webp", "image/webp", webp(800, 900)));

        assertTrue(imageUrl.endsWith(".webp"));
    }

    private FileStorageService service(String url, String bucket, String key) throws IOException {
        uploadRoot = Files.createTempDirectory("uploads");
        return new FileStorageService(uploadRoot.toString(), url, bucket, key);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private MockMultipartFile jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", output)) {
            throw new IOException("jpeg");
        }
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", output.toByteArray());
    }

    private byte[] webp(int width, int height) {
        byte[] data = new byte[30];
        data[0] = 'R';
        data[1] = 'I';
        data[2] = 'F';
        data[3] = 'F';
        data[8] = 'W';
        data[9] = 'E';
        data[10] = 'B';
        data[11] = 'P';
        data[12] = 'V';
        data[13] = 'P';
        data[14] = '8';
        data[15] = 'X';
        data[16] = 10;
        int storedWidth = width - 1;
        int storedHeight = height - 1;
        data[24] = (byte) (storedWidth & 0xff);
        data[25] = (byte) ((storedWidth >> 8) & 0xff);
        data[26] = (byte) ((storedWidth >> 16) & 0xff);
        data[27] = (byte) (storedHeight & 0xff);
        data[28] = (byte) ((storedHeight >> 8) & 0xff);
        data[29] = (byte) ((storedHeight >> 16) & 0xff);
        return data;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
