package com.tounsiguide.locationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Location &amp; Matching Service (Sprint 2): the single source of truth for places.
 * Owns the Location directory, aliases, place search, geocoding and the matching rules.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LocationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationServiceApplication.class, args);
    }
}
