package com.tounsiguide.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";
    private static final String ISSUER = "tounsiguide-identity";

    private final JwtAuthFilter filter = new JwtAuthFilter(SECRET, ISSUER);

    private String token(String secret, String issuer, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-1")
                .claim("name", "Sarra Ben Ahmed é")
                .claim("roles", List.of("ADMIN", "MEMBER"))
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String validToken() throws Exception {
        return token(SECRET, ISSUER, Instant.now().plusSeconds(600));
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void aValidTokenGivesTheServicesTheUserIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer " + validToken());
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeader("X-User-Roles")).isEqualTo("ADMIN,MEMBER");
        assertThat(URLDecoder.decode(forwarded.getHeader("X-User-Name"), StandardCharsets.UTF_8))
                .isEqualTo("Sarra Ben Ahmed é");
    }

    @Test
    void headersForgedByTheClientAreReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer " + validToken());
        request.addHeader("X-User-Id", "someone-else");
        request.addHeader("x-user-roles", "ADMIN");
        MockFilterChain chain = new MockFilterChain();

        run(request, chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("user-1");
        assertThat(java.util.Collections.list(forwarded.getHeaders("X-User-Id"))).containsExactly("user-1");
    }

    @Test
    void publicReadsPassWithoutTokenAndForgedHeadersAreRemoved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.addHeader("X-User-Id", "someone-else");
        request.addHeader("X-User-Roles", "ADMIN");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("X-User-Roles")).isNull();
        assertThat(java.util.Collections.list(forwarded.getHeaderNames()))
                .noneMatch(name -> name.toLowerCase().startsWith("x-user-"));
    }

    @Test
    void writingWithoutATokenIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void theUsersRoutesNeedAToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");

        MockHttpServletResponse response = run(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void anExpiredTokenIsRefusedEvenOnAPublicRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer " + token(SECRET, ISSUER, Instant.now().minusSeconds(60)));

        MockHttpServletResponse response = run(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() throws Exception {
        String forged = token("another-secret-another-secret-another-1", ISSUER, Instant.now().plusSeconds(600));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer " + forged);

        MockHttpServletResponse response = run(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        String foreign = token(SECRET, "someone-else", Instant.now().plusSeconds(600));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer " + foreign);

        MockHttpServletResponse response = run(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void garbageInsteadOfATokenIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Authorization", "Bearer not-a-jwt");

        MockHttpServletResponse response = run(request, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void loginAndPreflightNeedNoToken() throws Exception {
        MockFilterChain loginChain = new MockFilterChain();
        MockHttpServletResponse login = run(new MockHttpServletRequest("POST", "/api/v1/auth/login"), loginChain);
        MockFilterChain preflightChain = new MockFilterChain();
        MockHttpServletResponse preflight = run(new MockHttpServletRequest("OPTIONS", "/api/v1/posts"), preflightChain);

        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(loginChain.getRequest()).isNotNull();
        assertThat(preflight.getStatus()).isEqualTo(200);
        assertThat(preflightChain.getRequest()).isNotNull();
    }

    @Test
    void aTooShortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtAuthFilter("short", ISSUER))
                .isInstanceOf(IllegalStateException.class);
    }
}
