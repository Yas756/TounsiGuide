package com.tounsiguide.locationservice.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token";

    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN);

    private MockFilterChain run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    void aRequestWithoutTheTokenIsRefusedEvenWithForgedUserHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts/1");
        request.addHeader("X-User-Id", "attacker");
        request.addHeader("X-User-Roles", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString())
                .contains("\"status\":401", "\"code\":\"UNAUTHORIZED\"", "\"path\":\"/api/v1/posts/1\"", "\"details\":{}");
    }

    @Test
    void aWrongTokenIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader(InternalTokenFilter.HEADER, TOKEN + "x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(run(request, response).getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void theRightTokenPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.addHeader(InternalTokenFilter.HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(run(request, response).getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void onlyHealthAndInfoAreOpen() throws Exception {
        for (String open : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/info"}) {
            assertThat(run(new MockHttpServletRequest("GET", open), new MockHttpServletResponse()).getRequest())
                    .as(open).isNotNull();
        }
        for (String closed : new String[]{"/actuator/env", "/internal/posts/1/changed", "/api/v1/media/x/file"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(run(new MockHttpServletRequest("GET", closed), response).getRequest()).as(closed).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void aBlankTokenIsRefusedAtStartup() {
        assertThatThrownBy(() -> new InternalTokenFilter(" ")).isInstanceOf(IllegalStateException.class);
    }
}
