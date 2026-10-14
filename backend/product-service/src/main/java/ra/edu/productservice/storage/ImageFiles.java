package ra.edu.productservice.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import ra.edu.productservice.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Kiểm tra ảnh dùng chung cho mọi nơi lưu: chỉ JPEG/PNG/WebP (theo chữ ký file, không tin
 * Content-Type client gửi), tối đa 2 MB; tên file do server sinh từ mã SKU.
 */
public final class ImageFiles {

    public static final long MAX_BYTES = 2 * 1024 * 1024;

    private ImageFiles() {
    }

    /** @return phần mở rộng đúng với nội dung file: jpg | png | webp. */
    public static String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("File ảnh trống");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("Ảnh tối đa 2 MB");
        }
        try (InputStream in = file.getInputStream()) {
            return detectExtension(in.readNBytes(12)).orElseThrow(() -> invalid("Chỉ nhận ảnh JPEG, PNG hoặc WebP"));
        } catch (IOException e) {
            throw invalid("Không đọc được file ảnh");
        }
    }

    /** Tên file không đoán được: "s24u-gry-256-1a2b3c4d" (không phần mở rộng). */
    public static String newBaseName(String skuCode) {
        String base = skuCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (base.isEmpty() ? "sku" : base) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String contentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    static Optional<String> detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return Optional.of("png");
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    static BusinessException invalid(String message) {
        return new BusinessException("INVALID_IMAGE", message, HttpStatus.BAD_REQUEST);
    }
}
