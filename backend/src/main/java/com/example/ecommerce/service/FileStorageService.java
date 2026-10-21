package com.example.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int MIN_IMAGE_EDGE = 800;

    private final Path uploadRoot;
    private final String supabaseUrl;
    private final String bucket;
    private final String serviceRoleKey;
    private final HttpClient httpClient;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.storage-bucket:}") String storageBucket,
            @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.supabaseUrl = normalizeBase(supabaseUrl);
        this.bucket = storageBucket == null ? "" : storageBucket.trim();
        this.serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리를 준비할 수 없습니다.", e);
        }
    }

    public String store(MultipartFile file) {
        return store(file, "", true);
    }

    public String storeReviewImage(MultipartFile file) {
        return store(file, "review", false);
    }

    public String requireReviewObjectUrl(String imageUrl) {
        String prefix = publicPrefix() + "review/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("리뷰 이미지 주소가 올바르지 않습니다.");
        }
        String name = imageUrl.substring(prefix.length());
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("리뷰 이미지 주소가 올바르지 않습니다.");
        }
        return prefix + name;
    }

    private String store(MultipartFile file, String folder, boolean requireCatalogSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지 파일이 없습니다.");
        }

        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("jpg, jpeg, png, gif, webp 이미지만 업로드할 수 있습니다.");
        }
        requireSupabaseReady();
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("이미지 파일은 2MB 이하여야 합니다.");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        String objectPath = folder == null || folder.isBlank() ? filename : folder + "/" + filename;
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
        }
        int[] edges = readImageEdges(bytes, extension);
        if (edges == null) {
            throw new IllegalArgumentException("이미지를 확인할 수 없습니다.");
        }
        if (requireCatalogSize && (edges[0] < MIN_IMAGE_EDGE || edges[1] < MIN_IMAGE_EDGE)) {
            throw new IllegalArgumentException("이미지는 800×800px 이상이어야 합니다.");
        }

        HttpRequest request = authorized(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
                .header("Content-Type", contentType(file, extension))
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(storageFailureMessage(response));
        }
        return publicPrefix() + objectPath;
    }

    public void deleteIfStored(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (canUseSupabase() && imageUrl.startsWith(publicPrefix())) {
            String filename = objectName(imageUrl);
            if (filename != null) {
                deleteRemoteQuietly(filename);
            }
            return;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return;
        }
        String filename = imageUrl;
        int slash = Math.max(imageUrl.lastIndexOf('/'), imageUrl.lastIndexOf('\\'));
        if (slash >= 0) {
            filename = imageUrl.substring(slash + 1);
        }
        if (filename.isBlank() || filename.contains("..")) {
            return;
        }
        Path target = uploadRoot.resolve(filename).normalize();
        if (!target.startsWith(uploadRoot)) {
            return;
        }
        java.io.File file = target.toFile();
        if (file.exists()) {
            file.delete();
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    private void requireSupabaseReady() {
        if (!supabaseConfigured()) {
            throw new IllegalStateException(
                    "이미지 저장 설정이 없습니다. SUPABASE_URL, SUPABASE_STORAGE_BUCKET, SUPABASE_SERVICE_ROLE_KEY를 확인해 주세요.");
        }
        if (!supabaseUrl.startsWith("https://") && !supabaseUrl.startsWith("http://")) {
            throw new IllegalStateException("SUPABASE_URL은 http:// 또는 https:// 로 시작해야 합니다.");
        }
        if (!bucket.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("SUPABASE_STORAGE_BUCKET 값이 올바르지 않습니다.");
        }
    }

    private boolean canUseSupabase() {
        return supabaseConfigured()
                && (supabaseUrl.startsWith("https://") || supabaseUrl.startsWith("http://"))
                && bucket.matches("[A-Za-z0-9._-]+");
    }

    private boolean supabaseConfigured() {
        return !supabaseUrl.isBlank() && !bucket.isBlank() && !serviceRoleKey.isBlank();
    }

    private String publicPrefix() {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/";
    }

    private String objectName(String imageUrl) {
        String name = imageUrl.substring(publicPrefix().length());
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int hash = name.indexOf('#');
        if (hash >= 0) {
            name = name.substring(0, hash);
        }
        if (!name.matches("(?:review/)?[A-Za-z0-9._-]+")) {
            return null;
        }
        return name;
    }

    private void deleteRemoteQuietly(String filename) {
        HttpRequest request = authorized(supabaseUrl + "/storage/v1/object/" + bucket + "/" + filename)
                .DELETE()
                .build();
        try {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();
            if ((status < 200 || status >= 300) && status != 404) {
                log.warn("Supabase image delete failed with status {}", status);
            }
        } catch (IllegalStateException e) {
            log.warn("Supabase image delete failed");
        }
    }

    private HttpRequest.Builder authorized(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장소에 연결하지 못했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("이미지 저장소에 연결하지 못했습니다.", e);
        }
    }

    private String storageFailureMessage(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ").trim();
        if (!serviceRoleKey.isBlank()) {
            body = body.replace(serviceRoleKey, "[redacted]");
        }
        if (body.length() > 180) {
            body = body.substring(0, 180);
        }
        if (body.isBlank()) {
            return "이미지 저장에 실패했습니다.";
        }
        return "이미지 저장에 실패했습니다. " + body;
    }

    private int[] readImageEdges(byte[] bytes, String extension) {
        if (".webp".equals(extension)) {
            return readWebpEdges(bytes);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            return null;
        }
    }

    private int[] readWebpEdges(byte[] data) {
        if (data.length < 30 || !matchesAscii(data, 0, "RIFF") || !matchesAscii(data, 8, "WEBP")) {
            return null;
        }
        if (matchesAscii(data, 12, "VP8X")) {
            int width = 1 + (data[24] & 0xff) + ((data[25] & 0xff) << 8) + ((data[26] & 0xff) << 16);
            int height = 1 + (data[27] & 0xff) + ((data[28] & 0xff) << 8) + ((data[29] & 0xff) << 16);
            return new int[]{width, height};
        }
        if (matchesAscii(data, 12, "VP8 ") && data.length >= 30) {
            for (int index = 20; index < data.length - 6; index++) {
                if ((data[index] & 0xff) == 0x9d && (data[index + 1] & 0xff) == 0x01 && (data[index + 2] & 0xff) == 0x2a) {
                    int width = ((data[index + 3] & 0xff) | ((data[index + 4] & 0xff) << 8)) & 0x3fff;
                    int height = ((data[index + 5] & 0xff) | ((data[index + 6] & 0xff) << 8)) & 0x3fff;
                    return new int[]{width, height};
                }
            }
            return null;
        }
        if (matchesAscii(data, 12, "VP8L") && data.length >= 25 && data[20] == 0x2f) {
            int b0 = data[21] & 0xff;
            int b1 = data[22] & 0xff;
            int b2 = data[23] & 0xff;
            int b3 = data[24] & 0xff;
            int width = 1 + (b0 | ((b1 & 0x3f) << 8));
            int height = 1 + (((b1 & 0xc0) >> 6) | (b2 << 2) | ((b3 & 0x0f) << 10));
            return new int[]{width, height};
        }
        return null;
    }

    private boolean matchesAscii(byte[] data, int offset, String text) {
        if (offset + text.length() > data.length) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if (data[offset + index] != (byte) text.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String contentType(MultipartFile file, String extension) {
        String type = file.getContentType();
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return type;
        }
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private static String normalizeBase(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/rest/v1")) {
            value = value.substring(0, value.length() - "/rest/v1".length());
        }
        if (value.endsWith("/storage/v1")) {
            value = value.substring(0, value.length() - "/storage/v1".length());
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
