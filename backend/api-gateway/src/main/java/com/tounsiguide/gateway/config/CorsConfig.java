package com.tounsiguide.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * @param allowedOrigins {@code tounsiguide.cors.allowed-origin-patterns} (env {@code CORS_ALLOWED_ORIGINS}),
     *                       comma separated; default: the Angular dev server on any port of localhost / 127.0.0.1
     */
    @Bean
    public CorsFilter corsFilter(
            @Value("${tounsiguide.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Video players read these on partial (Range) answers of /api/v1/media/{id}/file.
        configuration.setExposedHeaders(List.of("Content-Range", "Accept-Ranges", "Content-Length"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new OrderedCorsFilter(source);
    }

    /**
     * Runs before the JWT filter, so that even a 401 answer carries the CORS headers
     * (otherwise the browser would report a CORS error instead of the 401).
     */
    static final class OrderedCorsFilter extends CorsFilter implements Ordered {

        OrderedCorsFilter(CorsConfigurationSource source) {
            super(source);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
