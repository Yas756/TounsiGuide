package com.tounsiguide.locationservice.association;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** The change ping, the analysis and the startup migration run on Spring's task executor ({@code @Async}). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
