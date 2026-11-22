package com.tounsiguide.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts two stub backends and checks which one each path reaches. The association route must be declared
 * before the community posts route (association-api.md, section 7), and /internal/** must not be routed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteOrderTest {

    private static final String SECRET = "route-test-secret-route-test-secret-1234";
    private static final String INTERNAL_TOKEN = "route-test-internal-token";

    private static final Backend COMMUNITY = new Backend("community");
    private static final Backend LOCATION = new Backend("location");
    private static final Backend BUSINESS = new Backend("business");

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("tounsiguide.services.community-url", COMMUNITY::url);
        registry.add("tounsiguide.services.location-url", LOCATION::url);
        registry.add("tounsiguide.services.identity-url", () -> "http://localhost:1");
        registry.add("tounsiguide.services.business-url", BUSINESS::url);
        registry.add("tounsiguide.security.jwt.secret", () -> SECRET);
        registry.add("tounsiguide.security.internal-token", () -> INTERNAL_TOKEN);
    }

    @AfterAll
    static void stopBackends() {
        COMMUNITY.stop();
        LOCATION.stop();
        BUSINESS.stop();
    }

    @BeforeEach
    void reset() {
        COMMUNITY.paths.clear();
        LOCATION.paths.clear();
        BUSINESS.paths.clear();
        BUSINESS.internalTokens.clear();
        COMMUNITY.internalTokens.clear();
    }

    @Test
    void theBackendReceivesTheGatewayInternalTokenNeverTheClientOne() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/posts"))
                .header("X-Internal-Token", "guessed-by-the-client")
                .GET()
                .build();

        http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(COMMUNITY.internalTokens).containsExactly(List.of(INTERNAL_TOKEN));
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token())
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String token() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("tounsiguide-identity")
                .subject("user-1")
                .claim("roles", List.of("MEMBER"))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void postAssociationsGoToTheLocationService() throws Exception {
        assertThat(send("GET", "/api/v1/posts/1/associations").body()).isEqualTo("location");
        assertThat(send("POST", "/api/v1/posts/1/associations").body()).isEqualTo("location");
        assertThat(send("POST", "/api/v1/posts/1/associations/rescan").body()).isEqualTo("location");
        assertThat(send("POST", "/api/v1/posts/1/associations/admin").body()).isEqualTo("location");
        assertThat(COMMUNITY.paths).isEmpty();
    }

    @Test
    void postsAndCommentsStillGoToTheCommunityService() throws Exception {
        assertThat(send("GET", "/api/v1/posts").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/posts/1").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/posts/1/comments").body()).isEqualTo("community");
        assertThat(LOCATION.paths).isEmpty();
    }

    @Test
    void mediaGoesToTheCommunityService() throws Exception {
        assertThat(send("POST", "/api/v1/media").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/media/abc").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/media/abc/file").body()).isEqualTo("community");
        assertThat(LOCATION.paths).isEmpty();
    }

    @Test
    void associationsAndLocationsGoToTheLocationService() throws Exception {
        assertThat(send("GET", "/api/v1/associations/summary?postIds=1,2").body()).isEqualTo("location");
        assertThat(send("POST", "/api/v1/associations/7/dispute").body()).isEqualTo("location");
        assertThat(send("GET", "/api/v1/associations").body()).isEqualTo("location");
        assertThat(send("GET", "/api/v1/locations/14/posts").body()).isEqualTo("location");
        assertThat(LOCATION.paths).contains("/api/v1/associations/summary", "/api/v1/locations/14/posts");
    }

    @Test
    void businessPagesGoToTheBusinessServiceWithTheInternalToken() throws Exception {
        assertThat(send("GET", "/api/v1/business-pages").body()).isEqualTo("business");
        assertThat(send("GET", "/api/v1/business-pages/3").body()).isEqualTo("business");
        assertThat(send("GET", "/api/v1/business-pages/3/wall").body()).isEqualTo("business");
        assertThat(send("POST", "/api/v1/business-pages/3/wall/9/hide").body()).isEqualTo("business");
        assertThat(send("GET", "/api/v1/business-pages/by-location/14").body()).isEqualTo("business");
        assertThat(send("POST", "/api/v1/business-pages/requests").body()).isEqualTo("business");
        assertThat(send("POST", "/api/v1/business-pages/requests/1/approve").body()).isEqualTo("business");
        assertThat(BUSINESS.internalTokens).allMatch(tokens -> tokens.equals(List.of(INTERNAL_TOKEN)));
        assertThat(COMMUNITY.paths).isEmpty();
        assertThat(LOCATION.paths).isEmpty();
    }

    @Test
    void reportsGoToTheCommunityService() throws Exception {
        assertThat(send("POST", "/api/v1/reports").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/reports/queue").body()).isEqualTo("community");
        assertThat(send("POST", "/api/v1/reports/targets/POST/12/restrict").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/modules").body()).isEqualTo("community");
        assertThat(send("GET", "/api/v1/modules/cafes").body()).isEqualTo("community");
        assertThat(send("POST", "/api/v1/modules/3/approve").body()).isEqualTo("community");
        assertThat(LOCATION.paths).isEmpty();
        assertThat(BUSINESS.paths).isEmpty();
    }

    @Test
    void businessPagesAreReadableWithoutATokenButChangesNeedOne() throws Exception {
        HttpResponse<String> read = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/business-pages/3/wall")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> write = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/business-pages/requests"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(read.body()).isEqualTo("business");
        assertThat(write.statusCode()).isEqualTo(401);
        assertThat(BUSINESS.paths).containsExactly("/api/v1/business-pages/3/wall");
    }

    @Test
    void internalEndpointsAreNotRouted() throws Exception {
        HttpResponse<String> response = send("POST", "/internal/posts/1/changed");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(COMMUNITY.paths).isEmpty();
        assertThat(LOCATION.paths).isEmpty();
        assertThat(BUSINESS.paths).isEmpty();
    }

    /** A tiny HTTP server that answers its own name and remembers the paths it received. */
    private static final class Backend {

        private final HttpServer server;
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final List<List<String>> internalTokens = new CopyOnWriteArrayList<>();

        Backend(String name) {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/", exchange -> {
                paths.add(exchange.getRequestURI().getPath());
                internalTokens.add(exchange.getRequestHeaders().getOrDefault("X-Internal-Token", List.of()));
                exchange.getRequestBody().readAllBytes();
                byte[] body = name.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }
    }
}
