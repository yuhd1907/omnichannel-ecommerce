package ra.edu.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.PageData;
import ra.edu.productservice.dto.BrandSummaryDto;
import ra.edu.productservice.dto.CategorySummaryDto;
import ra.edu.productservice.dto.ProductDetailDto;
import ra.edu.productservice.dto.ProductSummaryDto;
import ra.edu.productservice.entity.Product;
import ra.edu.productservice.exception.ResourceNotFoundException;
import ra.edu.productservice.repository.BrandRepository;
import ra.edu.productservice.repository.CategoryRepository;
import ra.edu.productservice.repository.ProductRepository;

import java.util.List;
import java.util.UUID;

/** Truy vấn cho trang quản trị (thấy cả sản phẩm ngừng bán) + danh mục / thương hiệu cho form. */
@Service
@RequiredArgsConstructor
public class ProductAdminQueryService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public PageData<ProductSummaryDto> list(String q, String status, Pageable pageable) {
        Page<Product> page = productRepository.searchForAdmin(
                q == null ? "" : q.trim(), status == null ? "" : status.trim().toUpperCase(), pageable);
        return new PageData<>(page.map(ProductSummaryDto::from).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductDetailDto get(UUID id) {
        return productRepository.findByIdWithDetails(id).map(ProductDetailDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<CategorySummaryDto> categories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategorySummaryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BrandSummaryDto> brands() {
        return brandRepository.findAll(Sort.by("name")).stream().map(BrandSummaryDto::from).toList();
    }
}
