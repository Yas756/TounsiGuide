package com.tounsiguide.communityservice.location;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PostChangeNotifierTest {

    /** Nothing listens on port 1: every call fails to connect. */
    private final PostChangeNotifier notifier = new PostChangeNotifier("http://localhost:1", "test-internal-token");

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        notifier.destroy();
    }

    @Test
    void anUnreachableLocationServiceNeverFailsTheCaller() {
        assertThatCode(() -> notifier.send(5L)).doesNotThrowAnyException();
        assertThatCode(() -> notifier.postChanged(5L)).doesNotThrowAnyException();
    }

    @Test
    void insideATransactionThePingWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.postChanged(5L);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void noPostIdNoPing() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.postChanged(null);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
