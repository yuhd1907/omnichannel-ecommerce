package ra.edu.orderservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ra.edu.orderservice.entity.Inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findBySkuCode(String skuCode);

    /**
     * Atomic reserve: trừ available_qty, cộng reserved_qty, tăng version.
     * Mệnh đề WHERE available_qty >= :q đảm bảo database từ chối khi không đủ hàng.
     *
     * @return số dòng bị ảnh hưởng — 1 = thành công, 0 = hết hàng.
     */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.availableQty = i.availableQty - :q,
                i.reservedQty  = i.reservedQty  + :q,
                i.version      = i.version      + 1
            WHERE i.skuCode = :sku
              AND i.availableQty >= :q
            """)
    int reserve(@Param("sku") String skuCode, @Param("q") long quantity);

    /**
     * Confirm: chuyển reserved → sold (giảm reserved_qty).
     * Gọi khi thanh toán thành công.
     */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.reservedQty = i.reservedQty - :q,
                i.version     = i.version     + 1
            WHERE i.skuCode = :sku
              AND i.reservedQty >= :q
            """)
    int confirm(@Param("sku") String skuCode, @Param("q") long quantity);

    /**
     * Release: hoàn hàng khi đơn bị huỷ (reserved → available).
     */
    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.availableQty = i.availableQty + :q,
                i.reservedQty  = i.reservedQty  - :q,
                i.version      = i.version      + 1
            WHERE i.skuCode = :sku
              AND i.reservedQty >= :q
            """)
    int release(@Param("sku") String skuCode, @Param("q") long quantity);
}
