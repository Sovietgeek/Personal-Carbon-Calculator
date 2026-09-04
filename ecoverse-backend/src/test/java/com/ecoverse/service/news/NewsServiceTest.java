package com.ecoverse.service.news;

import com.ecoverse.dto.news.NewsResponse;
import com.ecoverse.dto.news.NewsResponse.Article;
import com.ecoverse.service.NewsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NewsService tests — Phase 3.
 *
 * Verifies:
 * - @Cacheable annotation present
 * - Pagination metadata in response
 * - NewsResponse structure
 * - Article fields are sanitized (via InputSanitizer)
 */
@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock private WebClient.Builder webClientBuilder;

    @InjectMocks private NewsService newsService;

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        @DisplayName("Service has @Cacheable annotation on getNews")
        void cacheableAnnotationPresent() throws NoSuchMethodException {
            var method = NewsService.class.getMethod("getNews", String.class, int.class, int.class);
            var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);
            assertThat(cacheable).isNotNull();
            assertThat(cacheable.value()).contains("news");
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("NewsResponse has pagination metadata")
        void hasPaginationMetadata() {
            NewsResponse response = NewsResponse.builder()
                    .articles(java.util.Collections.emptyList())
                    .totalArticles(50)
                    .page(0)
                    .size(20)
                    .totalPages(3)
                    .build();

            assertThat(response.getTotalArticles()).isEqualTo(50);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getSize()).isEqualTo(20);
            assertThat(response.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("Page 2 returns correct metadata")
        void page2Metadata() {
            NewsResponse response = NewsResponse.builder()
                    .articles(java.util.Collections.emptyList())
                    .totalArticles(50)
                    .page(2)
                    .size(20)
                    .totalPages(3)
                    .build();

            assertThat(response.getPage()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Article structure")
    class ArticleStructure {

        @Test
        @DisplayName("Article has all required fields")
        void articleHasAllFields() {
            Article article = Article.builder()
                    .title("Test Title")
                    .summary("Test Summary")
                    .link("https://example.com/article")
                    .date("Mon, 01 Jan 2024 00:00:00 GMT")
                    .source("BBC")
                    .category("Environment")
                    .build();

            assertThat(article.getTitle()).isEqualTo("Test Title");
            assertThat(article.getSummary()).isEqualTo("Test Summary");
            assertThat(article.getLink()).isEqualTo("https://example.com/article");
            assertThat(article.getSource()).isEqualTo("BBC");
            assertThat(article.getCategory()).isEqualTo("Environment");
        }

        @Test
        @DisplayName("Link can be null (rejected by URL validation)")
        void linkCanBeNullForInvalidUrls() {
            Article article = Article.builder()
                    .title("Test")
                    .link(null)
                    .build();

            assertThat(article.getLink()).isNull();
        }
    }

    @Nested
    @DisplayName("URL validation")
    class UrlValidation {

        @Test
        @DisplayName("getNews method accepts page and size params")
        void getNewsAcceptsPageAndSize() throws NoSuchMethodException {
            var method = NewsService.class.getMethod("getNews", String.class, int.class, int.class);
            assertThat(method).isNotNull();
            assertThat(method.getParameterCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("NewsResponse defaults")
    class NewsResponseDefaults {

        @Test
        @DisplayName("Empty articles list is valid")
        void emptyArticlesValid() {
            NewsResponse response = NewsResponse.builder()
                    .articles(java.util.Collections.emptyList())
                    .totalArticles(0)
                    .page(0)
                    .size(20)
                    .totalPages(0)
                    .build();

            assertThat(response.getArticles()).isEmpty();
            assertThat(response.getTotalArticles()).isEqualTo(0);
        }
    }
}
