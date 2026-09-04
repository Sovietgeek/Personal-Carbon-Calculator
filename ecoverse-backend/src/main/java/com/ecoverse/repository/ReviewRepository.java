package com.ecoverse.repository;

import com.ecoverse.model.Review;
import com.ecoverse.model.Review.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByProductIdAndStatus(Long productId, ReviewStatus status);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId AND r.status = 'APPROVED'")
    Double avgRatingByProductId(@Param("productId") Long productId);

    // ===== ADMIN QUERIES =====

    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    long countByStatus(ReviewStatus status);

    long countByProductId(Long productId);
}
