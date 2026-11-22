package com.tounsiguide.communityservice.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import com.tounsiguide.communityservice.common.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Tells the Location &amp; Matching Service that a post or one of its comments changed
 * ({@code POST /internal/posts/{postId}/changed}, see docs/api/association-api.md). It then reads the post
 * and updates the author's place claim and the place mentions.
 *
 * <p>Fire-and-forget: the call is sent after the transaction commits (so the Location Service reads the new
 * data), on a background thread, and a failure is only logged. It never fails or slows the user's request;
 * an admin "rescan" repairs a missed ping.</p>
 */
@Component
public class PostChangeNotifier implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PostChangeNotifier.class);

    private final RestClient restClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PostChangeNotifier(
            @Value("${tounsiguide.services.location-url:http://localhost:8082}") String locationServiceUrl,
            @Value("${tounsiguide.security.internal-token}") String internalToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(locationServiceUrl).requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
    }

    public void postChanged(Long postId) {
        if (postId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendInBackground(postId);
                }
            });
        } else {
            sendInBackground(postId);
        }
    }

    private void sendInBackground(Long postId) {
        try {
            executor.execute(() -> send(postId));
        } catch (RejectedExecutionException exception) {
            log.warn("Change of post {} not sent to the Location Service (shutting down)", postId);
        }
    }

    void send(Long postId) {
        try {
            restClient.post().uri("/internal/posts/{postId}/changed", postId).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("Could not notify the Location Service that post {} changed: {}", postId, exception.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
