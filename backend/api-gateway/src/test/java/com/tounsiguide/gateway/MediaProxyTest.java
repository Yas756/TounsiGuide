package com.tounsiguide.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Photos and videos through the Gateway: a large multipart upload reaches the community-service intact, the Range
 * header is forwarded and the 206 answer comes back, and the browser (Angular on 4200) is allowed by CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaProxyTest {

    private static final String SECRET = "media-test-secret-media-test-secret-1234";
    private static final String ANGULAR = "http://localhost:4200";

    private static final CommunityStub COMMUNITY = new CommunityStub();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("tounsiguide.services.community-url", COMMUNITY::url);
        registry.add("tounsiguide.services.location-url", () -> "http://localhost:1");
        registry.add("tounsiguide.services.identity-url", () -> "http://localhost:1");
        registry.add("tounsiguide.security.jwt.secret", () -> SECRET);
    }

    @AfterAll
    static void stopBackend() {
        COMMUNITY.server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
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

    private static byte[] multipart(String boundary, int fileSize) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"video.mp4\"\r\n"
                + "Content-Type: video/mp4\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[fileSize]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    @Test
    void aLargeMultipartUploadIsStreamedIntactToTheCommunityService() throws Exception {
        String boundary = "----tounsiguide-boundary";
        byte[] body = multipart(boundary, 30 * 1024 * 1024);

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/media"))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Origin", ANGULAR)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("received " + body.length);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ANGULAR);
        // The Gateway may rewrite the spacing and add a charset, but the boundary must be kept.
        assertThat(COMMUNITY.lastRequest.get("content-type"))
                .startsWith("multipart/form-data")
                .contains("boundary=" + boundary);
        assertThat(COMMUNITY.lastRequest.get("x-user-id")).isEqualTo("user-1");
    }

    @Test
    void anAnonymousUploadIsRefusedByTheGateway() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/media"))
                .header("Content-Type", "multipart/form-data; boundary=x")
                .POST(HttpRequest.BodyPublishers.ofString("--x--"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void theRangeHeaderIsForwardedAndThePartialAnswerComesBack() throws Exception {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri("/api/v1/media/abc/file"))
                .header("Range", "bytes=0-9")
                .header("Origin", ANGULAR)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(COMMUNITY.lastRequest.get("range")).isEqualTo("bytes=0-9");
        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.body()).hasSize(10);
        assertThat(response.headers().firstValue("Content-Range")).contains("bytes 0-9/100");
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers").orElse(""))
                .contains("Content-Range").contains("Accept-Ranges");
    }

    @Test
    void corsAllowsAMultipartUploadFromTheAngularApp() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/media"))
                .header("Origin", ANGULAR)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ANGULAR);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("POST");
    }

    /** Answers like the community-service: 201 with the number of bytes received, or 206 for a Range request. */
    private static final class CommunityStub {

        private final HttpServer server;
        private final Map<String, String> lastRequest = new ConcurrentHashMap<>();

        CommunityStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/", exchange -> {
                lastRequest.clear();
                exchange.getRequestHeaders().forEach((name, values) ->
                        lastRequest.put(name.toLowerCase(), values.getFirst()));
                byte[] received = exchange.getRequestBody().readAllBytes();
                byte[] answer;
                int status;
                if ("POST".equals(exchange.getRequestMethod())) {
                    status = 201;
                    answer = ("received " + received.length).getBytes(StandardCharsets.US_ASCII);
                } else if (exchange.getRequestHeaders().getFirst("Range") != null) {
                    status = 206;
                    answer = new byte[10];
                    exchange.getResponseHeaders().add("Content-Range", "bytes 0-9/100");
                    exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
                    exchange.getResponseHeaders().add("Content-Type", "video/mp4");
                } else {
                    status = 200;
                    answer = new byte[100];
                }
                exchange.sendResponseHeaders(status, answer.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(answer);
                }
            });
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }
    }
}
