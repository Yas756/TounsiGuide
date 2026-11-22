package com.tounsiguide.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S2-B10: the allowed origins come from configuration, not from the code. */
class CorsConfigTest {

    private static MockHttpServletResponse preflight(CorsFilter filter, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/posts");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void theConfiguredOriginIsAllowedAndAnotherIsNot() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(List.of("https://tounsiguide.example", " "));

        assertThat(preflight(filter, "https://tounsiguide.example").getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("https://tounsiguide.example");
        MockHttpServletResponse other = preflight(filter, "http://localhost:4200");
        assertThat(other.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(other.getStatus()).isEqualTo(403);
    }

    @Test
    void theDefaultPatternsAllowTheAngularDevServerOnAnyPort() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(List.of("http://localhost:*", "http://127.0.0.1:*"));

        assertThat(preflight(filter, "http://localhost:4200").getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:4200");
        assertThat(preflight(filter, "http://127.0.0.1:4300").getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://127.0.0.1:4300");
    }
}
