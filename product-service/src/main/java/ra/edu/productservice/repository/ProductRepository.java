package ra.edu.productservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import ra.edu.productservice.entity.Product;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand LEFT JOIN FETCH p.skus WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand LEFT JOIN FETCH p.skus WHERE p.slug = :slug")
    Optional<Product> findBySlugWithDetails(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"category", "brand"})
    @NonNull
    Page<Product> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByCategoryIdAndStatus(UUID categoryId, String status, Pageable pageable);
}
