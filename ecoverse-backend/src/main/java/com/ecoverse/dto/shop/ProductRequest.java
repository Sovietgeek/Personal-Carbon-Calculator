package com.ecoverse.dto.shop;

import com.ecoverse.model.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
    private String name;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(min = 1, max = 50, message = "Category must be between 1 and 50 characters")
    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be positive")
    private BigDecimal price;

    @Size(max = 2000, message = "Image URL must be at most 2000 characters")
    private String imageUrl;

    @Min(value = 1, message = "Eco rating must be between 1 and 5")
    @Max(value = 5, message = "Eco rating must be between 1 and 5")
    private Integer ecoRating;

    private Boolean isSecondhand;

    @Min(value = 0, message = "Stock must be non-negative")
    @Builder.Default
    private Integer stock = 0;

    private ProductStatus status;

    // Enhanced fields
    @Size(max = 100, message = "Brand must be at most 100 characters")
    private String brand;

    @DecimalMin(value = "0.01", message = "MRP must be positive")
    private BigDecimal mrp;

    @Min(value = 0, message = "Discount must be between 0 and 99")
    @Max(value = 99, message = "Discount must be between 0 and 99")
    private Integer discountPercent;

    private List<String> features;

    @Size(max = 500, message = "Highlights must be at most 500 characters")
    private String highlights;

    @Size(max = 500, message = "Tags must be at most 500 characters")
    private String tags;

    @DecimalMin(value = "1.0", message = "Rating must be between 1.0 and 5.0")
    @DecimalMax(value = "5.0", message = "Rating must be between 1.0 and 5.0")
    private BigDecimal rating;

    @Min(value = 0, message = "Rating count must be non-negative")
    private Integer ratingCount;

    @Min(value = 1, message = "Delivery days must be at least 1")
    private Integer deliveryDays;

    @Min(value = 1, message = "Weight must be positive")
    private Integer weightGrams;
}
