package ra.edu.productservice.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Nơi lưu ảnh sản phẩm. Database chỉ giữ URL ảnh (product_skus.image_url, products.image_url).
 * - CloudinaryImageStorage: khi có CLOUDINARY_URL -> ảnh lên Cloudinary, phân phối qua CDN.
 * - LocalImageStorage: mặc định -> ảnh trên volume product_uploads, phục vụ qua product-service.
 */
public interface ImageStorage {

    /** Kiểm tra + lưu ảnh; @return URL công khai để ghi vào database. */
    String store(MultipartFile file, String skuCode);

    /** Xóa ảnh cũ nếu do chính nơi lưu này quản lý (URL ngoài thì bỏ qua). */
    void deleteIfManaged(String url);
}
