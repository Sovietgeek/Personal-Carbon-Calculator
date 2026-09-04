package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.news.NewsResponse;
import com.ecoverse.service.NewsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@Validated
public class NewsController {

    @Autowired
    private NewsService newsService;

    @GetMapping
    public ResponseEntity<ApiResponse<NewsResponse>> getNews(
            @RequestParam(defaultValue = "all") String source,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "in") String country,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        NewsResponse response = newsService.getNews(source, category, country, city, state, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
