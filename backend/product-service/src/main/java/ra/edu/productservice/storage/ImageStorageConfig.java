package ra.edu.productservice.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chọn nơi lưu ảnh: có CLOUDINARY_URL -> Cloudinary; không có -> ổ đĩa (volume product_uploads).
 * LocalImageStorage luôn tồn tại để vẫn phục vụ ảnh đã tải lên trước khi chuyển sang cloud.
 */
@Slf4j
@Configuration
public class ImageStorageConfig {

    @Bean
    public LocalImageStorage localImageStorage(@Value("${app.upload.dir:./uploads}") String dir) {
        return new LocalImageStorage(dir);
    }

    @Bean
    @Primary
    public ImageStorage imageStorage(LocalImageStorage local,
                                     @Value("${app.upload.cloudinary-url:}") String cloudinaryUrl,
                                     @Value("${app.upload.cloudinary-folder:gom/products}") String folder,
                                     JsonMapper jsonMapper) {
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            log.info("Product images: local disk ({}) - set CLOUDINARY_URL to use Cloudinary", "app.upload.dir");
            return local;
        }
        CloudinaryImageStorage cloud = new CloudinaryImageStorage(cloudinaryUrl, folder, jsonMapper);
        log.info("Product images: Cloudinary (cloud={}, folder={})", cloud.cloudName(), folder);
        return cloud;
    }
}
