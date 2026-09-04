package com.ecoverse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSP regression guard for the frontend.
 *
 * The application serves a strict Content-Security-Policy with
 * `script-src 'self'` (no 'unsafe-inline'). Inline event handler
 * attributes (onclick=, onchange=, onsubmit=, oninput=, ...) are
 * blocked by the browser under that policy, which silently breaks
 * UI interactions (e.g. the Account/Settings tab).
 *
 * All interactivity must go through the external event delegation
 * system in js/events.js (data-action / data-action-change /
 * data-action-input attributes).
 *
 * This test FAILS if any inline event handler attribute is found in
 * the served index.html, or if a dynamic inline handler pattern is
 * present in the JS module files.
 */
class InlineHandlerCspRegressionTest {

    private static final Path STATIC_DIR = Paths.get("src/main/resources/static").toAbsolutePath();

    /**
     * Inline event handler attributes that are blocked by `script-src 'self'`.
     */
    private static final Pattern[] FORBIDDEN_HTML_ATTRIBUTES = {
            Pattern.compile("\\sonclick\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonchange\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\soninput\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonload\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonmouseover\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonmouseout\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonfocus\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonblur\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonsubmit\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonkeyup\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonkeydown\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sonkeypress\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sondblclick\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\soncontextmenu\\s*=", Pattern.CASE_INSENSITIVE)
    };

    /**
     * Dynamic inline handlers inside JS template literals — these are also
     * blocked by CSP once rendered into the DOM.
     */
    private static final Pattern[] FORBIDDEN_JS_TEMPLATE_PATTERNS = {
            Pattern.compile("onclick\\s*=\\s*['\"]?\\$\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onchange\\s*=\\s*['\"]?\\$\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("oninput\\s*=\\s*['\"]?\\$\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:\\s*(alert|eval|document\\.)", Pattern.CASE_INSENSITIVE)
    };

    @Test
    @DisplayName("index.html must not contain inline event handler attributes")
    void indexHtmlHasNoInlineEventHandlers() throws IOException {
        Path indexHtml = STATIC_DIR.resolve("index.html");
        assertThat(indexHtml).as("index.html must exist").exists();

        String content = Files.readString(indexHtml, StandardCharsets.UTF_8);
        List<String> violations = findViolations(content, FORBIDDEN_HTML_ATTRIBUTES);

        assertThat(violations)
                .as("Inline event handlers are blocked by strict CSP (script-src 'self'). "
                        + "Use data-action attributes handled by js/events.js instead.")
                .isEmpty();
    }

    @ParameterizedTest(name = "JS module {0} must not contain dynamic inline handlers")
    @ValueSource(strings = {
            "js/app.js", "js/events.js", "js/utils.js", "js/api.js", "js/theme.js",
            "js/dashboard.js", "js/carbon.js", "js/health.js", "js/weather.js",
            "js/news.js", "js/ai.js", "js/achievements.js", "js/profile.js",
            "js/shop.js", "js/seller.js", "js/admin.js"
    })
    @DisplayName("JS modules must not generate inline event handlers")
    void jsModulesHaveNoDynamicInlineHandlers(String modulePath) throws IOException {
        Path module = STATIC_DIR.resolve(modulePath);
        assertThat(module).as("%s must exist", modulePath).exists();

        String content = Files.readString(module, StandardCharsets.UTF_8);
        List<String> violations = findViolations(content, FORBIDDEN_JS_TEMPLATE_PATTERNS);

        assertThat(violations)
                .as("%s must not generate inline event handlers via template literals", modulePath)
                .isEmpty();
    }

    @Test
    @DisplayName("Chart.js must be served locally, not from external CDN")
    void chartJsServedLocally() throws IOException {
        Path indexHtml = STATIC_DIR.resolve("index.html");
        String content = Files.readString(indexHtml, StandardCharsets.UTF_8);

        assertThat(content)
                .as("Chart.js must be loaded from local static assets for strict CSP")
                .doesNotContain("cdn.jsdelivr.net");

        Path chartJs = STATIC_DIR.resolve("js/chart.umd.min.js");
        assertThat(chartJs).as("Local Chart.js asset must exist").exists();

        String chartContent = Files.readString(chartJs, StandardCharsets.UTF_8);
        assertThat(chartContent)
                .as("Vendored Chart.js must not reference an external source map (would trigger CSP connect-src violation)")
                .doesNotContain("sourceMappingURL");
    }

    @Test
    @DisplayName("events.js must be loaded after app.js (last script tag)")
    void eventsJsLoadedLast() throws IOException {
        Path indexHtml = STATIC_DIR.resolve("index.html");
        String content = Files.readString(indexHtml, StandardCharsets.UTF_8);

        int eventsIndex = content.indexOf("js/events.js");
        int appIndex = content.indexOf("js/app.js");
        int chartIndex = content.indexOf("js/chart.umd.min.js");

        assertThat(chartIndex)
                .as("Chart.js script tag must be present")
                .isGreaterThan(-1);
        assertThat(appIndex)
                .as("app.js script tag must be present")
                .isGreaterThan(-1);
        assertThat(eventsIndex)
                .as("events.js script tag must be present")
                .isGreaterThan(-1);
        assertThat(eventsIndex)
                .as("events.js must be loaded AFTER app.js so all handlers exist")
                .isGreaterThan(appIndex);
    }

    @Test
    @DisplayName("Registration UX must not require email activation")
    void registrationDoesNotRequireEmailActivation() throws IOException {
        Path indexHtml = STATIC_DIR.resolve("index.html");
        Path appJs = STATIC_DIR.resolve("js/app.js");
        String html = Files.readString(indexHtml, StandardCharsets.UTF_8);
        String app = Files.readString(appJs, StandardCharsets.UTF_8);

        assertThat(html)
                .as("The active registration screen must not ask users to check email")
                .doesNotContain("Check your email to verify your account")
                .doesNotContain("activate your account");
        assertThat(app)
                .as("Registration success must describe immediate sign-in")
                .contains("Account created successfully. Please sign in with your email and password.")
                .doesNotContain("Registration successful! Please check your email");
    }

    @Test
    @DisplayName("API transport must not HTML-encode request credentials")
    void apiTransportKeepsCredentialValuesUnchanged() throws IOException {
        Path apiJs = STATIC_DIR.resolve("js/api.js");
        String content = Files.readString(apiJs, StandardCharsets.UTF_8);

        assertThat(content)
                .as("API JSON bodies must be serialized without HTML transformations")
                .contains("config.body = JSON.stringify(body);")
                .doesNotContain("config.body = JSON.stringify(sanitizeObject(body));");
    }

    private static List<String> findViolations(String content, Pattern[] patterns) {
        List<String> violations = new ArrayList<>();
        String[] lines = content.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    violations.add("line " + (i + 1) + ": " + line.trim());
                    break;
                }
            }
        }
        return violations;
    }
}
