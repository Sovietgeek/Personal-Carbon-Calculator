package com.ecoverse.security;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.service.AuthService;
import com.ecoverse.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * Handles successful Google OAuth2 login.
 *
 * SECURITY: Tokens are NEVER sent in URL query parameters.
 * Instead, we use a short-lived one-time authorization code:
 *
 *   1. Google OAuth succeeds → we generate a random one-time code
 *   2. We store the JWT tokens server-side against this code
 *   3. We redirect to frontend with ONLY the code in the URL
 *   4. Frontend makes a POST /api/auth/oauth2/exchange with the code
 *   5. Backend returns the access token in body + sets refresh token as httpOnly cookie
 *
 * The refresh token is set as an httpOnly cookie on the exchange endpoint,
 * NOT on this redirect (which goes to the frontend, not the API).
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    @Lazy
    @Autowired
    private AuthService authService;

    @Autowired
    private OAuth2AuthorizationCodeService authCodeService;

    @Value("${app.cors.allowed-origins:http://localhost:8081}")
    private String allowedOrigins;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                          Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        // Extract Google user info
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");
        String googleSub = oauthUser.getAttribute("sub");

        logger.info("Google OAuth2 login attempt: email={}", email);

        try {
            // Process OAuth login — find or create user
            AuthResponse authResponse = authService.processOAuthLogin(email, name, picture, googleSub);

            // Generate a one-time authorization code
            String oneTimeCode = UUID.randomUUID().toString();

            // Store tokens server-side against this code
            // The refresh token will be set as an httpOnly cookie during the exchange
            authCodeService.store(oneTimeCode,
                    authResponse.getAccessToken(),
                    authResponse.getRefreshToken(),
                    authResponse.getUser().getId());

            // Redirect to frontend root with ONLY the one-time code (NOT the tokens)
            String redirectUrl = allowedOrigins.split(",")[0].trim();
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                    .queryParam("code", oneTimeCode)
                    .build().toUriString();

            logger.info("Google OAuth2 login successful: email={}, redirecting with one-time code", email);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            logger.error("Google OAuth2 login failed: {}", e.getMessage());
            String redirectUrl = allowedOrigins.split(",")[0].trim();
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl + "/login")
                    .queryParam("error", "oauth_login_failed")
                    .build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }
}
