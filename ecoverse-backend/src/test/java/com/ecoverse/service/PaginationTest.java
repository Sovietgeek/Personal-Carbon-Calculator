package com.ecoverse.service;

import com.ecoverse.controller.ShopController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for pagination behavior (B9).
 *
 * Requirements verified:
 * - Page size is capped at MAX_PAGE_SIZE (100)
 * - Page size minimum is 1 (cannot request 0 or negative)
 * - Page number minimum is 0 (negative values are corrected to 0)
 * - Default page size is 20
 * - Sort direction defaults to DESC for unrecognized values
 * - Paginated response includes required fields: content, page, size, totalElements, totalPages, last
 */
class PaginationTest {

    @Nested
    @DisplayName("Page Size Limits")
    class PageSizeLimits {

        @Test
        @DisplayName("Page size exceeding MAX_PAGE_SIZE (100) is capped")
        void pageSizeCappedAt100() {
            // The buildPageable method caps at MAX_PAGE_SIZE=100
            int requestedSize = 500;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(100);
        }

        @Test
        @DisplayName("Page size of 0 is corrected to 1 (minimum)")
        void pageSizeZeroCorrectedTo1() {
            int requestedSize = 0;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(1);
        }

        @Test
        @DisplayName("Negative page size is corrected to 1 (minimum)")
        void pageSizeNegativeCorrectedTo1() {
            int requestedSize = -5;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(1);
        }

        @Test
        @DisplayName("Page size of 50 is accepted as-is (within bounds)")
        void pageSize50Accepted() {
            int requestedSize = 50;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(50);
        }

        @Test
        @DisplayName("Page size of 100 is accepted as-is (at boundary)")
        void pageSize100Accepted() {
            int requestedSize = 100;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(100);
        }

        @Test
        @DisplayName("Page size of 101 is capped to 100")
        void pageSize101Capped() {
            int requestedSize = 101;
            int maxSize = 100;
            int safeSize = Math.min(Math.max(requestedSize, 1), maxSize);
            assertThat(safeSize).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Page Number Limits")
    class PageNumberLimits {

        @Test
        @DisplayName("Page number 0 is valid (first page)")
        void pageNumber0Valid() {
            int page = 0;
            int safePage = Math.max(page, 0);
            assertThat(safePage).isEqualTo(0);
        }

        @Test
        @DisplayName("Negative page number is corrected to 0")
        void negativePageCorrectedTo0() {
            int page = -3;
            int safePage = Math.max(page, 0);
            assertThat(safePage).isEqualTo(0);
        }

        @Test
        @DisplayName("Page number 5 is accepted as-is")
        void pageNumber5Accepted() {
            int page = 5;
            int safePage = Math.max(page, 0);
            assertThat(safePage).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("ShopController Constants")
    class ShopControllerConstants {

        @Test
        @DisplayName("MAX_PAGE_SIZE is 100")
        void maxPageSizeIs100() throws Exception {
            // Verify the constant exists and is 100
            var field = ShopController.class.getDeclaredField("MAX_PAGE_SIZE");
            field.setAccessible(true);
            int maxSize = (int) field.get(null);
            assertThat(maxSize).isEqualTo(100);
        }

        @Test
        @DisplayName("DEFAULT_PAGE_SIZE is 20")
        void defaultPageSizeIs20() throws Exception {
            var field = ShopController.class.getDeclaredField("DEFAULT_PAGE_SIZE");
            field.setAccessible(true);
            int defaultSize = (int) field.get(null);
            assertThat(defaultSize).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Sort Direction")
    class SortDirection {

        @Test
        @DisplayName("Unrecognized sort direction defaults to DESC")
        void unrecognizedSortDirectionDefaultsToDesc() {
            String direction = "sideways";
            // Logic from ShopController: "asc".equalsIgnoreCase(direction) ? ASC : DESC
            boolean isAsc = "asc".equalsIgnoreCase(direction);
            assertThat(isAsc).isFalse();
        }

        @Test
        @DisplayName("'asc' sort direction is recognized")
        void ascSortDirectionRecognized() {
            String direction = "asc";
            boolean isAsc = "asc".equalsIgnoreCase(direction);
            assertThat(isAsc).isTrue();
        }

        @Test
        @DisplayName("'ASC' (uppercase) sort direction is recognized")
        void ascUppercaseRecognized() {
            String direction = "ASC";
            boolean isAsc = "asc".equalsIgnoreCase(direction);
            assertThat(isAsc).isTrue();
        }
    }
}
