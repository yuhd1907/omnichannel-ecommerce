package ra.edu.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ra.edu.productservice.entity.ProductSku;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {

    Optional<ProductSku> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);

    List<ProductSku> findByProductId(UUID productId);

    List<ProductSku> findBySkuCodeIn(Collection<String> skuCodes);

    @Query("SELECT s FROM ProductSku s JOIN FETCH s.product WHERE s.skuCode = :skuCode")
    Optional<ProductSku> findBySkuCodeWithProduct(@Param("skuCode") String skuCode);

    @Query("SELECT s FROM ProductSku s JOIN FETCH s.product WHERE s.skuCode IN :skuCodes")
    List<ProductSku> findBySkuCodeInWithProduct(@Param("skuCodes") Collection<String> skuCodes);
}
