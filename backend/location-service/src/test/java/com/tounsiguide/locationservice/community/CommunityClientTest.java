package com.tounsiguide.locationservice.community;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** The post list of the Community Service is paged: findAllPosts reads every page. */
class CommunityClientTest {

    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<String> tokens = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/posts", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            queries.add(query);
            tokens.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")));
            String body = query.startsWith("page=0")
                    ? page(List.of(3L, 2L), 0, 2)
                    : page(List.of(1L), 1, 2);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String page(List<Long> ids, int page, int totalPages) {
        StringBuilder content = new StringBuilder();
        for (Long id : ids) {
            if (!content.isEmpty()) {
                content.append(',');
            }
            content.append("{\"id\":").append(id).append(",\"content\":\"post ").append(id)
                    .append("\",\"authorId\":\"u\",\"locationId\":").append(id).append(",\"publicationStatus\":\"PUBLISHED\",\"media\":[]}");
        }
        return "{\"content\":[" + content + "],\"page\":" + page + ",\"size\":50,\"totalElements\":3,\"totalPages\":"
                + totalPages + "}";
    }

    @Test
    void findAllPostsReadsEveryPage() {
        CommunityClient client = new CommunityClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-internal-token");

        List<CommunityClient.PostSnapshot> posts = client.findAllPosts();

        assertThat(posts).extracting(CommunityClient.PostSnapshot::id).containsExactly(3L, 2L, 1L);
        assertThat(tokens).containsOnly("test-internal-token");
        assertThat(queries).containsExactly("page=0&size=" + CommunityClient.PAGE_SIZE, "page=1&size=" + CommunityClient.PAGE_SIZE);
    }
}
