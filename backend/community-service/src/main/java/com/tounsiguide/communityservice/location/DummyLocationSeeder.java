package com.tounsiguide.communityservice.location;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DummyLocationSeeder implements CommandLineRunner {

    private final DummyLocationRepository locationRepository;

    public DummyLocationSeeder(DummyLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public void run(String... args) {
        if (locationRepository.count() > 0) {
            return;
        }

        locationRepository.saveAll(List.of(
                new DummyLocation(
                        "Café Example",
                        "A demonstration café location.",
                        "Tunis"
                ),
                new DummyLocation(
                        "Medina Restaurant",
                        "A demonstration restaurant location.",
                        "Tunis"
                ),
                new DummyLocation(
                        "Sidi Bou Said Park",
                        "A demonstration park location.",
                        "Sidi Bou Said"
                )
        ));
    }
}
