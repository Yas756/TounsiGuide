package com.tounsiguide.communityservice.location;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Both calls of the Community Service to the Location Service carry the internal token (2026-11-15). */
class LocationCallsTokenTest {

    private static final String TOKEN = "test-internal-token";

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
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

    @Test
    void theLocationCheckAndTheChangePingSendTheToken() {
        new LocationClient(url(), TOKEN).requireExists(12L);
        PostChangeNotifier notifier = new PostChangeNotifier(url(), TOKEN);
        try {
            notifier.send(5L);
        } finally {
            notifier.destroy();
        }

        assertThat(received).containsExactly(
                "GET /api/v1/locations/12 " + TOKEN,
                "POST /internal/posts/5/changed " + TOKEN);
    }
}
