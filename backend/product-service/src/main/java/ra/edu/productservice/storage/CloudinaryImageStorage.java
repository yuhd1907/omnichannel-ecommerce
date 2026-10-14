package ra.edu.productservice.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import ra.edu.productservice.exception.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lưu ảnh trên Cloudinary (object storage + CDN) qua REST API, upload có chữ ký:
 * signature = SHA-1(các tham số sắp theo tên, nối "k=v&..." + api_secret). api_secret chỉ nằm ở server.
 *
 * Cấu hình bằng một biến: CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
 * (copy nguyên từ trang Dashboard của Cloudinary).
 *
 * URL lưu vào database có thêm f_auto,q_auto,c_limit,w_1200: CDN tự chọn định dạng nhẹ (WebP/AVIF)
 * theo trình duyệt, nén tự động và giới hạn chiều rộng 1200px.
 */
@Slf4j
public class CloudinaryImageStorage implements ImageStorage {

    private static final String API = "https://api.cloudinary.com/v1_1/";
    private static final String DELIVERY_TRANSFORM = "f_auto,q_auto,c_limit,w_1200";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final JsonMapper jsonMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CloudinaryImageStorage(String cloudinaryUrl, String folder, JsonMapper jsonMapper) {
        URI uri = URI.create(cloudinaryUrl.trim());
        if (!"cloudinary".equals(uri.getScheme()) || uri.getUserInfo() == null || !uri.getUserInfo().contains(":")
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "CLOUDINARY_URL must look like cloudinary://<api_key>:<api_secret>@<cloud_name>");
        }
        String[] creds = uri.getUserInfo().split(":", 2);
        this.apiKey = creds[0];
        this.apiSecret = creds[1];
        this.cloudName = uri.getHost();
        this.folder = folder;
        this.jsonMapper = jsonMapper;
    }

    public String cloudName() {
        return cloudName;
    }

    @Override
    public String store(MultipartFile file, String skuCode) {
        String ext = ImageFiles.validate(file);
        String publicId = folder + "/" + ImageFiles.newBaseName(skuCode);
        Map<String, String> params = new TreeMap<>();
        params.put("public_id", publicId);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ImageFiles.invalid("Không đọc được file ảnh");
        }

        String boundary = "----gom" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        params.forEach((k, v) -> field(body, boundary, k, v));
        field(body, boundary, "api_key", apiKey);
        field(body, boundary, "signature", sign(params, apiSecret));
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload." + ext
                + "\"\r\nContent-Type: " + ImageFiles.contentType(ext) + "\r\n\r\n");
        body.writeBytes(bytes);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder(URI.create(API + cloudName + "/image/upload"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        JsonNode json = send(request, "upload");
        String secureUrl = json.path("secure_url").asString("");
        if (secureUrl.isEmpty()) {
            throw cloudError("Cloudinary không trả về secure_url");
        }
        log.info("Uploaded image to Cloudinary: publicId={}", json.path("public_id").asString(""));
        return secureUrl.replace("/image/upload/", "/image/upload/" + DELIVERY_TRANSFORM + "/");
    }

    @Override
    public void deleteIfManaged(String url) {
        String publicId = publicIdOf(url);
        if (publicId == null) {
            return;   // ảnh local cũ / URL ngoài / ảnh ở cloud khác
        }
        Map<String, String> params = new TreeMap<>();
        params.put("public_id", publicId);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        String form = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"))
                + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&signature=" + sign(params, apiSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + cloudName + "/image/destroy"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            send(request, "destroy");
        } catch (RuntimeException e) {
            // ảnh mồ côi trên cloud vô hại; không làm hỏng thao tác tải ảnh mới
            log.warn("Could not delete old Cloudinary image {}: {}", publicId, e.getMessage());
        }
    }

    /** "https://res.cloudinary.com/<cloud>/image/upload/<transform>/v123/gom/products/x.jpg" -> "gom/products/x". */
    String publicIdOf(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = Pattern.compile("^https://res\\.cloudinary\\.com/" + Pattern.quote(cloudName)
                + "/image/upload/(?:[^/]+/)*?(?:v\\d+/)?(" + Pattern.quote(folder) + "/[^/.]+)\\.[a-z0-9]+$").matcher(url);
        return m.matches() ? m.group(1) : null;
    }

    static String sign(Map<String, String> params, String secret) {
        String toSign = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + secret;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = jsonMapper.readTree(res.body());
            if (res.statusCode() / 100 != 2) {
                String msg = json.path("error").path("message").asString("HTTP " + res.statusCode());
                throw cloudError("Cloudinary " + action + " thất bại: " + msg);
            }
            return json;
        } catch (IOException e) {
            throw cloudError("Không kết nối được Cloudinary: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cloudError("Bị ngắt khi gọi Cloudinary");
        }
    }

    private static BusinessException cloudError(String message) {
        return new BusinessException("IMAGE_STORE_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
