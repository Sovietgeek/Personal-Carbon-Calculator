package com.ecoverse.dto.shop;

import com.ecoverse.model.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private Long sellerId;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String imageUrl;
    private Integer ecoRating;
    private Boolean isSecondhand;
    private Boolean isAvailable;
    private Integer stock;
    private ProductStatus status;
    private Integer version;
    private LocalDateTime createdAt;

    // Enhanced fields (Amazon-like)
    private String brand;
    private BigDecimal mrp;
    private Integer discountPercent;
    private List<String> features;
    private String highlights;
    private String tags;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer deliveryDays;
    private Integer weightGrams;
}
