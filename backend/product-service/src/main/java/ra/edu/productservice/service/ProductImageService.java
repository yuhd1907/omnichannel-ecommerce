package ra.edu.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ra.edu.productservice.dto.ProductSkuDto;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.entity.ProductSku;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.repository.ProductSkuRepository;
import ra.edu.productservice.storage.ImageStorage;

import java.time.Instant;

/** Admin tải ảnh cho một SKU; sản phẩm chưa có ảnh đại diện thì lấy luôn ảnh này. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductSkuRepository productSkuRepository;
    private final ImageStorage imageStorage;

    @Transactional
    public ProductSkuDto uploadSkuImage(String skuCode, MultipartFile file) {
        ProductSku sku = productSkuRepository.findBySkuCodeWithProduct(skuCode)
                .orElseThrow(() -> new ResourceNotFoundException("SKU_NOT_FOUND", "SKU not found with skuCode: " + skuCode));

        String oldUrl = sku.getImageUrl();
        String newUrl = imageStorage.store(file, skuCode);
        sku.setImageUrl(newUrl);
        sku.setUpdatedAt(Instant.now());

        Product product = sku.getProduct();
        if (product.getImageUrl() == null || product.getImageUrl().equals(oldUrl)) {
            product.setImageUrl(newUrl);   // ảnh đại diện đang trống hoặc đang là ảnh cũ của chính SKU này
        }
        productSkuRepository.save(sku);

        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            imageStorage.deleteIfManaged(oldUrl);
        }
        log.info("Uploaded image for sku={}: {}", skuCode, newUrl);
        return ProductSkuDto.from(sku);
    }
}
