package com.tounsiguide.businessservice.client;

import com.sun.net.httpserver.HttpServer;
import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The three clients against a stub HTTP server: paths, internal token, 404 and "service down" handling. */
class ClientsTest {

    private static final String TOKEN = "test-internal-token";

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();
    /** path + query -> [status, body] */
    private final Map<String, Object[]> answers = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String uri = exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query);
            received.add(exchange.getRequestMethod() + " " + uri + " " + exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getRequestBody().readAllBytes();
            Object[] answer = answers.getOrDefault(uri, new Object[]{404, ""});
            byte[] body = ((String) answer[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders((int) answer[0], body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A port where nothing listens. */
    private static final String DOWN = "http://127.0.0.1:1";

    @Test
    void locationClientReadsALocationWithTheTokenAnd404IsEmpty() {
        answers.put("/api/v1/locations/14", new Object[]{200,
                "{\"id\":14,\"name\":\"Café\",\"city\":\"Tunis\",\"address\":null,\"category\":\"cafe\",\"aliases\":[]}"});
        LocationClient client = new LocationClient(url(), TOKEN);

        assertThat(client.findLocation(14L)).get().extracting(LocationClient.LocationSnapshot::name).isEqualTo("Café");
        assertThat(client.findLocation(99L)).isEmpty();
        assertThat(received).containsExactly("GET /api/v1/locations/14 " + TOKEN, "GET /api/v1/locations/99 " + TOKEN);
    }

    @Test
    void locationClientReadsEveryPageOfConfirmedPostIds() {
        answers.put("/api/v1/locations/14/posts?page=0&size=100", new Object[]{200,
                "{\"content\":[9,8],\"page\":0,\"size\":100,\"totalElements\":3,\"totalPages\":2}"});
        answers.put("/api/v1/locations/14/posts?page=1&size=100", new Object[]{200,
                "{\"content\":[3],\"page\":1,\"size\":100,\"totalElements\":3,\"totalPages\":2}"});

        assertThat(new LocationClient(url(), TOKEN).confirmedPostIds(14L)).containsExactly(9L, 8L, 3L);
    }

    @Test
    void locationClientDownIs503() {
        LocationClient client = new LocationClient(DOWN, TOKEN);

        assertThatThrownBy(() -> client.findLocation(14L)).isInstanceOf(DependencyUnavailableException.class)
                .extracting("code").isEqualTo("LOCATION_SERVICE_UNAVAILABLE");
        assertThatThrownBy(() -> client.confirmedPostIds(14L)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void communityClientReadsPostsInBatchesOfFiftyWithTheToken() {
        String first = String.join(",", IntStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList());
        answers.put("/api/v1/posts/batch?ids=" + first, new Object[]{200,
                "[{\"id\":1,\"content\":\"Bon café\",\"authorId\":\"u\",\"authorName\":\"Sarra\",\"locationId\":14,"
                        + "\"createdAt\":\"2026-10-01T10:00:00Z\",\"publicationStatus\":\"PUBLISHED\",\"media\":[]}]"});
        answers.put("/api/v1/posts/batch?ids=51", new Object[]{200, "[]"});

        List<CommunityClient.PostSnapshot> posts = new CommunityClient(url(), TOKEN)
                .findPublishedPosts(IntStream.rangeClosed(1, 51).mapToObj(Long::valueOf).toList());

        assertThat(posts).extracting(CommunityClient.PostSnapshot::content).containsExactly("Bon café");
        assertThat(received).hasSize(2).allMatch(line -> line.endsWith(" " + TOKEN));
    }

    @Test
    void communityClientDownIs503() {
        assertThatThrownBy(() -> new CommunityClient(DOWN, TOKEN).findPublishedPosts(List.of(1L)))
                .isInstanceOf(DependencyUnavailableException.class)
                .extracting("code").isEqualTo("COMMUNITY_SERVICE_UNAVAILABLE");
    }

    @Test
    void identityClientGrantsTheRoleWithTheToken() {
        answers.put("/internal/users/user-1/roles/BUSINESS_REP", new Object[]{200, "{}"});

        new IdentityClient(url(), TOKEN).grantBusinessRep("user-1");

        assertThat(received).containsExactly("POST /internal/users/user-1/roles/BUSINESS_REP " + TOKEN);
    }

    @Test
    void identityClientUnknownUserIs409AndDownIs503() {
        assertThatThrownBy(() -> new IdentityClient(url(), TOKEN).grantBusinessRep("ghost"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> new IdentityClient(DOWN, TOKEN).grantBusinessRep("user-1"))
                .isInstanceOf(DependencyUnavailableException.class)
                .extracting("code").isEqualTo("IDENTITY_SERVICE_UNAVAILABLE");
    }
}
