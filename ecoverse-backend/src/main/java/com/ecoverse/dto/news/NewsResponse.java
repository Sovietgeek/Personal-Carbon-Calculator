package com.ecoverse.dto.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsResponse {

    private List<Article> articles;
    private Integer totalArticles;
    private Integer page;
    private Integer size;
    private Integer totalPages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Article {
        private String title;
        private String summary;
        private String link;
        private String date;
        private String source;
        private String category;
        private String image;
        private String ecoTip;
        private String sentiment; // "positive" | "negative" | "neutral"
    }
}
