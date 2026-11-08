package com.tounsiguide.locationservice.location;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fills an empty directory on first start.
 *
 * <p>The first three entries are the Sprint 1 "dummy locations" that used to live in the Community
 * Service (S2-B09). They are inserted first and in the same order, so they get ids 1, 2 and 3 and the
 * {@code locationId} already stored on old posts still points to the same place.</p>
 */
@Component
public class DirectorySeeder implements CommandLineRunner {

    private final LocationRepository repository;

    public DirectorySeeder(LocationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        // Sprint 1 catalog, migrated from community-service (ids 1, 2, 3).
        save("Café Example", "Tunis", "Avenue Habib Bourguiba, Tunis", 36.8003, 10.1857, "cafe");
        save("Medina Restaurant", "Tunis", "Médina de Tunis", 36.7987, 10.1709, "restaurant");
        save("Sidi Bou Said Park", "Sidi Bou Saïd", "Sidi Bou Saïd", 36.8701, 10.3469, "park");

        // A few well-known places so the directory search is useful from the first demo.
        save("Médina de Tunis", "Tunis", "Tunis", 36.7983, 10.1700, "attraction", "Medina", "La Médina");
        save("Musée national du Bardo", "Tunis", "Le Bardo, Tunis", 36.8095, 10.1347, "museum", "Bardo", "Musée du Bardo");
        save("Avenue Habib Bourguiba", "Tunis", "Tunis", 36.8000, 10.1860, "street", "Avenue Bourguiba");
        save("Site archéologique de Carthage", "Carthage", "Carthage", 36.8529, 10.3233, "archaeological_site", "Carthage", "Thermes d'Antonin");
        save("Village de Sidi Bou Saïd", "Sidi Bou Saïd", "Sidi Bou Saïd", 36.8704, 10.3414, "village", "Sidi Bou Said");
        save("Lac de Tunis", "Tunis", "Les Berges du Lac, Tunis", 36.8320, 10.2330, "water", "Les Berges du Lac", "Le Lac");
        save("Amphithéâtre d'El Jem", "El Jem", "El Jem, Mahdia", 35.2965, 10.7069, "archaeological_site", "Colisée d'El Jem", "El Jem");
        save("Médina de Sousse", "Sousse", "Sousse", 35.8256, 10.6390, "attraction", "Sousse Medina");
        save("Port El Kantaoui", "Sousse", "Hammam Sousse", 35.8920, 10.5960, "marina", "El Kantaoui");
        save("Houmt Souk", "Djerba", "Djerba", 33.8750, 10.8570, "town", "Djerba Houmt Souk");
        save("Grande Mosquée de Kairouan", "Kairouan", "Kairouan", 35.6812, 10.1036, "place_of_worship", "Mosquée Okba", "Mosquée Uqba");
    }

    private void save(String name, String city, String address, double latitude, double longitude,
                      String category, String... aliases) {
        Location location = new Location(name, city, address, latitude, longitude, category,
                LocationSource.DIRECTORY, null, "seed");
        List.of(aliases).forEach(location::addAlias);
        repository.saveAndFlush(location);
    }
}
