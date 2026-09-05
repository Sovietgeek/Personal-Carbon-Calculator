package com.ecoverse.config;

import com.ecoverse.security.CsrfOriginValidationFilter;
import com.ecoverse.security.CustomUserDetailsService;
import com.ecoverse.security.JwtAuthenticationFilter;
import com.ecoverse.security.OAuth2FailureHandler;
import com.ecoverse.security.OAuth2SuccessHandler;
import com.ecoverse.security.RateLimitFilter;
import com.ecoverse.security.RequestIdFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private CsrfOriginValidationFilter csrfOriginValidationFilter;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OAuth2FailureHandler oAuth2FailureHandler;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.url:}")
    private String appUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                .requestMatchers("/api/auth/verify").permitAll()
                .requestMatchers("/api/auth/forgot-password").permitAll()
                .requestMatchers("/api/auth/reset-password").permitAll()
                .requestMatchers("/api/auth/resend-verification").permitAll()
                .requestMatchers("/api/auth/oauth2/exchange").permitAll()
                .requestMatchers("/api/auth/oauth-status").permitAll()
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers("/api/auth/me").authenticated()
                // Webhook endpoint — NO JWT auth (Razorpay calls this)
                .requestMatchers("/api/payments/webhook").permitAll()
                // Admin endpoints — ADMIN role enforced via @PreAuthorize
                .requestMatchers("/api/admin/**").authenticated()
                // Seller endpoints — SELLER/ADMIN role enforced via @PreAuthorize
                .requestMatchers("/api/seller/**").authenticated()
                // Actuator health/info endpoints — authenticated (shows details only to authorized users)
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                .requestMatchers("/api-docs/**").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/login/oauth2/code/**").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // OAuth2 Login (Google)
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            // Security Headers — Production-hardened CSP + additional security headers
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                        // Chart.js now served locally (no longer from jsdelivr CDN)
                        "script-src 'self' https://checkout.razorpay.com; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                        "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                        "img-src 'self' data: https:; " +
                        "frame-src https://api.razorpay.com; " +
                        // Only allow frontend connect to domains the app genuinely needs:
                        // geocoding for weather city search, Razorpay for payment processing
                        "connect-src 'self' https://geocoding-api.open-meteo.com " +
                            "https://api.razorpay.com https://checkout.razorpay.com; " +
                        "object-src 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"))
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable()) // Handled in custom header below
                .addHeaderWriter((request, response) -> {
                    // X-Content-Type-Options: nosniff
                    response.setHeader("X-Content-Type-Options", "nosniff");
                    // Referrer-Policy: strict-origin-when-cross-origin
                    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                    // Permissions-Policy: restrict sensitive features
                    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(self)");
                    // X-Permitted-Cross-Domain-Policies: none
                    response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
                    // Cross-Origin headers — "cross-origin" allows mobile browsers,
                    // WebViews, and proxy-based browsers (Opera Mini, UC Browser) to
                    // load resources without being blocked by CORP checks.
                    response.setHeader("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
                    response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
                    // Strict-Transport-Security: only for HTTPS requests (1 year, include subdomains)
                    if (request.isSecure()) {
                        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                    }
                })
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(csrfOriginValidationFilter, RateLimitFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, CsrfOriginValidationFilter.class)
            .addFilterBefore(requestIdFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = new java.util.ArrayList<>(Arrays.asList(allowedOrigins.split(",")));

        // Auto-add the app's own URL (set by Render as APP_URL from service host)
        // This ensures CORS works even if CORS_ORIGINS env var has a stale/wrong URL
        if (appUrl != null && !appUrl.isBlank()) {
            String normalizedUrl = appUrl.trim();
            // Render APP_URL may not include scheme — add https:// if missing
            if (!normalizedUrl.startsWith("http")) {
                normalizedUrl = "https://" + normalizedUrl;
            }
            if (!origins.contains(normalizedUrl)) {
                origins.add(normalizedUrl);
            }
        }

        // Remove any blank entries from misconfigured env vars
        origins.removeIf(String::isBlank);

        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With",
                "Accept", "Origin", "X-Idempotency-Key", "X-Razorpay-Signature"));
        configuration.setExposedHeaders(Arrays.asList("X-RateLimit-Limit", "X-RateLimit-Remaining",
                "X-RateLimit-Reset", "Set-Cookie", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
