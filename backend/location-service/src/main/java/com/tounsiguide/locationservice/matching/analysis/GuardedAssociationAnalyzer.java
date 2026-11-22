package com.tounsiguide.locationservice.matching.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Selection and safety around the analyzers (association-api.md, section 5.2).
 *
 * <ul>
 *   <li>The analyzer named by {@code tounsiguide.analysis.provider} answers first. An analyzer other than the
 *       rule-based one is only registered when its {@code @ConditionalOnProperty} matches that property.</li>
 *   <li>If it is missing, throws, takes longer than {@code tounsiguide.analysis.timeout}, or returns an
 *       invalid result, the {@code tounsiguide.analysis.fallback} analyzer answers. The result always says
 *       which provider produced it (A7).</li>
 *   <li>If the fallback fails too, an empty result is returned: the analysis never breaks the caller.</li>
 * </ul>
 */
@Component
public class GuardedAssociationAnalyzer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GuardedAssociationAnalyzer.class);

    private final AssociationAnalyzer selected;
    private final AssociationAnalyzer fallback;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GuardedAssociationAnalyzer(List<AssociationAnalyzer> analyzers, AnalysisProperties properties) {
        this.fallback = find(analyzers, properties.fallback())
                .or(() -> find(analyzers, RuleBasedAssociationAnalyzer.NAME))
                .orElseThrow(() -> new IllegalStateException("No fallback AssociationAnalyzer is registered"));
        this.selected = find(analyzers, properties.provider()).orElseGet(() -> {
            log.warn("Analyzer '{}' is not registered (turned off?), using fallback '{}'",
                    properties.provider(), fallback.name());
            return fallback;
        });
        this.timeout = properties.timeout();
        log.info("Association analysis ready: provider={}, fallback={}, timeout={}",
                selected.name(), fallback.name(), timeout);
    }

    private static Optional<AssociationAnalyzer> find(List<AssociationAnalyzer> analyzers, String name) {
        return analyzers.stream().filter(a -> a.name().equalsIgnoreCase(name)).findFirst();
    }

    public String selectedName() {
        return selected.name();
    }

    public AnalysisResult analyse(AnalysisContext context) {
        try {
            return validate(callWithTimeout(selected, context), context);
        } catch (RuntimeException exception) {
            if (selected == fallback) {
                log.warn("Analyzer '{}' failed for post {}: {}", selected.name(), postId(context), exception.getMessage());
                return empty(context, "Analyse impossible : " + exception.getMessage());
            }
            log.warn("Analyzer '{}' failed for post {} ({}), using fallback '{}'",
                    selected.name(), postId(context), exception.getMessage(), fallback.name());
        }
        try {
            return validate(callWithTimeout(fallback, context), context);
        } catch (RuntimeException exception) {
            log.warn("Fallback analyzer '{}' failed for post {}: {}", fallback.name(), postId(context), exception.getMessage());
            return empty(context, "Analyse impossible : " + exception.getMessage());
        }
    }

    private AnalysisResult callWithTimeout(AssociationAnalyzer analyzer, AnalysisContext context) {
        CompletableFuture<AnalysisResult> future = CompletableFuture.supplyAsync(() -> analyzer.analyse(context), executor);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AnalysisFailedException("timed out after " + timeout);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new AnalysisFailedException(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AnalysisFailedException("interrupted");
        }
    }

    /** Same checks as the contract test: scores 0..100, known Location ids only, reasoning not empty. */
    static AnalysisResult validate(AnalysisResult result, AnalysisContext context) {
        if (result == null) {
            throw new AnalysisFailedException("no result");
        }
        if (isBlank(result.provider()) || isBlank(result.reasoning())) {
            throw new AnalysisFailedException("provider or reasoning missing");
        }
        Set<Long> known = context.candidateLocations().stream()
                .map(AnalysisContext.CandidateLocation::id)
                .collect(Collectors.toSet());
        for (LocationVerdict verdict : result.verdicts()) {
            if (!known.contains(verdict.locationId())) {
                throw new AnalysisFailedException("unknown Location " + verdict.locationId());
            }
            if (outOfRange(verdict.supportScore()) || outOfRange(verdict.opposeScore())) {
                throw new AnalysisFailedException("score out of range for Location " + verdict.locationId());
            }
        }
        return result;
    }

    private AnalysisResult empty(AnalysisContext context, String reasoning) {
        return new AnalysisResult(fallback.name(), "none", List.of(), reasoning);
    }

    private static Object postId(AnalysisContext context) {
        return context.post() == null ? "?" : context.post().id();
    }

    private static boolean outOfRange(int score) {
        return score < 0 || score > 100;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    /** The analyzer did not produce a usable result. */
    static class AnalysisFailedException extends RuntimeException {
        AnalysisFailedException(String message) {
            super(message);
        }
    }
}
