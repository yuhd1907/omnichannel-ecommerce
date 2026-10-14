package ra.edu.productservice.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import ra.edu.productservice.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lưu ảnh trên đĩa (Docker volume product_uploads), phục vụ qua GET /api/v1/products/images/{tên}.
 * Dùng khi chưa cấu hình Cloudinary; vẫn phục vụ ảnh cũ sau khi đã chuyển sang Cloudinary.
 */
public class LocalImageStorage implements ImageStorage {

    /** Đường dẫn công khai qua gateway; GET /api/v1/products/** không cần JWT. */
    public static final String PUBLIC_PREFIX = "/api/v1/products/images/";
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,150}\\.(jpg|png|webp)$");

    private final Path dir;

    public LocalImageStorage(String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String skuCode) {
        String name = ImageFiles.newBaseName(skuCode) + "." + ImageFiles.validate(file);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("IMAGE_STORE_FAILED", "Không lưu được ảnh", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return PUBLIC_PREFIX + name;
    }

    /** File ảnh theo tên (đã kiểm tra tên an toàn, chống path traversal); rỗng nếu không có. */
    public Optional<Path> load(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path p = dir.resolve(name).normalize();
        return p.startsWith(dir) && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    @Override
    public void deleteIfManaged(String url) {
        if (url == null || !url.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        load(url.substring(PUBLIC_PREFIX.length())).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // ảnh mồ côi vô hại; không làm hỏng thao tác tải ảnh mới
            }
        });
    }
}
