package ra.edu.productservice.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ra.edu.productservice.exception.BusinessException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryImageStorageTest {

    private final CloudinaryImageStorage storage =
            new CloudinaryImageStorage("cloudinary://123456:secret@demo-cloud", "gom/products", JsonMapper.builder().build());

    @Test
    @DisplayName("Chữ ký khớp ví dụ trong tài liệu Cloudinary (tham số sắp theo tên + api_secret, SHA-1)")
    void sign_matchesCloudinaryDocExample() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", "1315060510");               // cố ý không theo thứ tự
        params.put("public_id", "sample_image");
        params.put("eager", "w_400,h_300,c_pad|w_260,h_200,c_crop");

        assertThat(CloudinaryImageStorage.sign(params, "abcd")).isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
    }

    @Test
    @DisplayName("Lấy public_id từ URL của chính cloud/folder; URL khác -> null (không xóa nhầm)")
    void publicIdOf_onlyOwnImages() {
        assertThat(storage.publicIdOf("https://res.cloudinary.com/demo-cloud/image/upload/f_auto,q_auto,c_limit,w_1200/v1712345678/gom/products/s24u-gry-256-1a2b3c4d.jpg"))
                .isEqualTo("gom/products/s24u-gry-256-1a2b3c4d");
        assertThat(storage.publicIdOf("https://res.cloudinary.com/demo-cloud/image/upload/v1/gom/products/x-1.png"))
                .isEqualTo("gom/products/x-1");
        assertThat(storage.publicIdOf("/api/v1/products/images/x.png")).isNull();
        assertThat(storage.publicIdOf("https://res.cloudinary.com/other-cloud/image/upload/v1/gom/products/x.png")).isNull();
        assertThat(storage.publicIdOf(null)).isNull();
    }

    @Test
    @DisplayName("CLOUDINARY_URL sai định dạng -> báo lỗi rõ ràng lúc khởi động")
    void invalidUrl_rejected() {
        assertThatThrownBy(() -> new CloudinaryImageStorage("https://api_key:secret@cloud", "f", JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cloudinary://");
    }

    @Test
    @DisplayName("File không phải ảnh (dù đuôi .png) bị chặn trước khi gọi Cloudinary")
    void notAnImage_rejectedBeforeUpload() {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> storage.store(fake, "SKU-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG hoặc WebP");
    }
}
