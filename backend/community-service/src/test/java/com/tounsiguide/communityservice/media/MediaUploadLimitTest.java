package com.tounsiguide.communityservice.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** In the real servlet container: a multipart body over the configured limit is answered 413 in the common format. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=2KB",
                "tounsiguide.media.dir=${java.io.tmpdir}/tounsiguide-media-test",
                "tounsiguide.security.internal-token=" + MediaUploadLimitTest.INTERNAL_TOKEN
        }
)
class MediaUploadLimitTest {

    static final String INTERNAL_TOKEN = "limit-test-internal-token";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aTooBigUploadGets413PayloadTooLarge() throws Exception {
        String boundary = "----tounsiguide";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[5 * 1024]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/media"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-User-Id", "user-1")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void anUnknownMediaFileIs404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/media/does-not-exist/file"))
                .header("X-Internal-Token", INTERNAL_TOKEN).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
    }

    @Test
    void aDirectCallWithoutTheInternalTokenIsRefusedEvenWithForgedAdminHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/posts/1"))
                .header("X-User-Id", "attacker").header("X-User-Roles", "ADMIN").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void aWrongInternalTokenIsRefused() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/posts"))
                .header("X-Internal-Token", INTERNAL_TOKEN + "x").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void healthStaysOpenForMonitoring() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotEqualTo(401);
    }
}
