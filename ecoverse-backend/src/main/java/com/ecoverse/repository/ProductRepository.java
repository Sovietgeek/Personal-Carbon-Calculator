package com.ecoverse.repository;

import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ===== Legacy queries (kept for backward compatibility) =====

    List<Product> findByIsAvailableTrue();

    List<Product> findByCategoryAndIsAvailableTrue(String category);

    Page<Product> findByIsAvailableTrue(Pageable pageable);

    Page<Product> findByCategoryAndIsAvailableTrue(String category, Pageable pageable);

    List<Product> findBySellerId(Long sellerId);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    // ===== Status-based queries (primary queries for Phase 4) =====

    /**
     * Find all products with a given status (paginated).
     * Used for shop listing (status=ACTIVE).
     */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * Find products by category and status (paginated).
     * Used for category filtering in the shop.
     */
    Page<Product> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable);

    /**
     * Find products by seller and status (paginated).
     * Used for seller's own product listing.
     */
    Page<Product> findBySellerIdAndStatus(Long sellerId, ProductStatus status, Pageable pageable);

    /**
     * Find all products by seller (all statuses, paginated).
     * Used for seller managing their own products.
     */
    Page<Product> findAllBySellerId(Long sellerId, Pageable pageable);

    // ===== Keyword search =====

    /**
     * Search products by keyword in name or description, filtered by status.
     * Case-insensitive search. Only returns ACTIVE products for shop listing.
     */
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
    Page<Product> searchByKeywordAndStatus(@Param("keyword") String keyword,
                                           @Param("status") ProductStatus status,
                                           Pageable pageable);

    /**
     * Search products by keyword in name or description, filtered by category and status.
     */
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
           "AND p.category = :category " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
    Page<Product> searchByKeywordAndCategoryAndStatus(@Param("keyword") String keyword,
                                                      @Param("category") String category,
                                                      @Param("status") ProductStatus status,
                                                      Pageable pageable);

    /**
     * Advanced product search with multiple filters.
     * All parameters are optional — null parameters are ignored.
     */
    @Query("SELECT p FROM Product p WHERE p.status = :status " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
           "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:ecoRating IS NULL OR p.ecoRating >= :ecoRating)")
    Page<Product> searchProducts(@Param("status") ProductStatus status,
                                 @Param("category") String category,
                                 @Param("keyword") String keyword,
                                 @Param("minPrice") java.math.BigDecimal minPrice,
                                 @Param("maxPrice") java.math.BigDecimal maxPrice,
                                 @Param("ecoRating") Integer ecoRating,
                                 Pageable pageable);

    // ===== Concurrency-safe stock operations =====

    /**
     * Find a product by ID with a pessimistic write lock.
     * Used during order creation to prevent concurrent stock modification.
     * The lock is held until the transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * Atomically decrement stock for a product.
     * Only succeeds if sufficient stock exists (stock >= qty).
     * Returns the number of affected rows (1 = success, 0 = insufficient stock).
     *
     * This is the concurrency-safe way to decrement stock without
     * read-modify-write race conditions. The WHERE clause ensures
     * atomicity — if stock changed between the check and this update,
     * the WHERE condition will fail and return 0 affected rows.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty, p.version = p.version + 1 " +
           "WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically restore stock for a product (e.g., on order cancellation).
     * Always succeeds as long as the product exists.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty, p.version = p.version + 1 " +
           "WHERE p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Set product status to OUT_OF_STOCK if stock is 0.
     * Called after stock decrement if the new stock is 0.
     */
    @Modifying
    @Query("UPDATE Product p SET p.status = 'OUT_OF_STOCK', p.version = p.version + 1 " +
           "WHERE p.id = :id AND p.stock = 0 AND p.status = 'ACTIVE'")
    int markOutOfStockIfZero(@Param("id") Long id);

    // ===== Batch loading (fix N+1 in cart) =====

    /**
     * Find multiple products by their IDs in a single query.
     * Used to batch-load products for cart items (fixes N+1 query).
     */
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllByIdIn(@Param("ids") List<Long> ids);

    // ===== Admin queries =====

    /**
     * Find all products regardless of status (for admin management).
     * Paginated, newest first.
     */
    Page<Product> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Search products by name (admin search, all statuses).
     */
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Find products by status (admin filter).
     */
    Page<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status, Pageable pageable);

    // ===== Admin aggregate queries =====

    long countByStatus(ProductStatus status);

    long countByStockLessThanEqual(int stock);
}
