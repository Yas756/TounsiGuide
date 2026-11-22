package com.tounsiguide.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S2-T03: when a service behind the Gateway is down, the browser gets a clean 503 in the common error format
 * (no stack trace, no Spring default body), naming which part of the platform is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceDownTest {

    /** A port where nothing listens: the connection is refused at once. */
    private static final String DOWN = "http://localhost:" + freePort();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("tounsiguide.services.community-url", () -> DOWN);
        registry.add("tounsiguide.services.location-url", () -> DOWN);
        registry.add("tounsiguide.services.identity-url", () -> DOWN);
        registry.add("tounsiguide.services.business-url", () -> DOWN);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aDownServiceGivesA503InTheCommonFormat() throws Exception {
        HttpResponse<String> response = get("/api/v1/posts");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("application/json"));
        assertThat(response.body())
                .contains("\"status\":503", "\"code\":\"SERVICE_UNAVAILABLE\"", "\"path\":\"/api/v1/posts\"", "\"details\":{}")
                .doesNotContain("trace", "Exception");
    }

    @Test
    void everyRouteAnswersTheSameWay() throws Exception {
        for (String path : new String[]{"/api/v1/locations?query=bardo", "/api/v1/associations/summary?postIds=1",
                "/api/v1/media/x/file", "/api/v1/business-pages/1/wall"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(503);
            assertThat(response.body()).as(path).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        }
    }
}
