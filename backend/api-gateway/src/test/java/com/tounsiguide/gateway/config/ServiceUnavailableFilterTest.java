package com.tounsiguide.gateway.config;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceUnavailableFilterTest {

    @Test
    void aConnectionProblemDeepInTheCausesIsFound() {
        ConnectException refused = new ConnectException("Connection refused");
        RuntimeException wrapped = new IllegalStateException("proxy failed", new UncheckedIOException(refused));

        assertThat(ServiceUnavailableFilter.unreachable(wrapped)).isSameAs(refused);
    }

    @Test
    void otherErrorsAreLeftToTheNormalHandling() {
        assertThat(ServiceUnavailableFilter.unreachable(new IllegalStateException("bug"))).isNull();
        assertThat(ServiceUnavailableFilter.unreachable(new RuntimeException(new NullPointerException()))).isNull();
    }
}
