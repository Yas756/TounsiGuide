package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.common.error.ConflictException;
import com.tounsiguide.locationservice.common.security.CurrentUser;
import com.tounsiguide.locationservice.geocoding.GeocodedPlace;
import com.tounsiguide.locationservice.geocoding.GeocodingProvider;
import com.tounsiguide.locationservice.geocoding.GeocodingUnavailableException;
import com.tounsiguide.locationservice.location.dto.CreateLocationRequest;
import com.tounsiguide.locationservice.location.dto.LocationResponse;
import com.tounsiguide.locationservice.location.dto.PlaceSearchResponse;
import com.tounsiguide.locationservice.location.dto.PlaceSuggestion;
import com.tounsiguide.locationservice.location.dto.ResolvePlaceRequest;
import com.tounsiguide.locationservice.matching.MatchingProvider;
import com.tounsiguide.locationservice.matching.PlaceCandidate;
import com.tounsiguide.locationservice.matching.TextNormalizer;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LocationService {

    /** Searches shorter than this are not sent to the geocoder (too many meaningless results). */
    static final int MIN_EXTERNAL_QUERY_LENGTH = 3;
    static final int MAX_LIMIT = 50;
    static final int MAX_IDS = 100;
    private static final int CANDIDATE_POOL = 200;

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository repository;
    private final LocationMapper mapper;
    private final MatchingProvider matcher;
    private final GeocodingProvider geocoder;

    public LocationService(
            LocationRepository repository,
            LocationMapper mapper,
            MatchingProvider matcher,
            GeocodingProvider geocoder
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.matcher = matcher;
        this.geocoder = geocoder;
        log.info("Location service ready: matching={}, geocoding={}", matcher.name(), geocoder.name());
    }

    /** Directory search by name, alias or city, best match first (S2-US03). No query: first Locations by name. */
    @Transactional(readOnly = true)
    public List<LocationResponse> searchDirectory(String query, int limit) {
        int size = clamp(limit);
        if (query == null || TextNormalizer.normalize(query).isEmpty()) {
            return repository.findAllByOrderByNameAsc(PageRequest.of(0, size)).stream()
                    .map(mapper::toResponse)
                    .toList();
        }
        return rank(query, size).stream().map(mapper::toResponse).toList();
    }

    /** Several Locations at once (the feed shows the place of every post). Unknown ids are skipped. */
    @Transactional(readOnly = true)
    public List<LocationResponse> getByIds(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(id -> id != null).distinct().limit(MAX_IDS).toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return repository.findByIdIn(distinct).stream()
                .sorted(Comparator.comparing(Location::getId))
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse getById(Long id) {
        return mapper.toResponse(findOrThrow(id));
    }

    /**
     * Search used by the place picker: the directory first, then places from the map provider that are
     * not already in the directory. If the provider is down, the directory still answers.
     */
    @Transactional(readOnly = true)
    public PlaceSearchResponse searchPlaces(String query, int limit, Double biasLatitude, Double biasLongitude) {
        int size = clamp(limit);
        List<Location> known = query == null ? List.of() : rank(query, size);
        List<LocationResponse> directory = known.stream().map(mapper::toResponse).toList();

        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_EXTERNAL_QUERY_LENGTH) {
            return new PlaceSearchResponse(directory, List.of(), true);
        }
        try {
            List<PlaceSuggestion> external = geocoder.search(trimmed, size, biasLatitude, biasLongitude).stream()
                    .filter(place -> known.stream().noneMatch(location -> matcher.isSamePlace(candidate(place), location)))
                    .map(PlaceSuggestion::from)
                    .toList();
            return new PlaceSearchResponse(directory, external, true);
        } catch (GeocodingUnavailableException exception) {
            log.warn("Geocoder unavailable for search '{}': {}", trimmed, exception.getMessage());
            return new PlaceSearchResponse(directory, List.of(), false);
        }
    }

    /**
     * The named place nearest to a map click.
     *
     * @throws GeocodingUnavailableException when the provider cannot be reached (answered with 503)
     */
    public Optional<PlaceSuggestion> reverse(double latitude, double longitude) {
        return geocoder.reverse(latitude, longitude).map(PlaceSuggestion::from);
    }

    /** Administrator adds a Location to the directory. Same name in the same city gives 409 (S2-US10). */
    @Transactional
    public LocationResponse create(CreateLocationRequest request, CurrentUser user) {
        user.requireAdmin();
        String name = TextNormalizer.normalize(request.name());
        String city = request.city() == null || request.city().isBlank() ? null : TextNormalizer.normalize(request.city());
        boolean duplicate = city == null
                ? repository.existsByNormalizedNameAndNormalizedCityIsNull(name)
                : repository.existsByNormalizedNameAndNormalizedCity(name, city);
        if (duplicate) {
            throw new ConflictException("A Location with this name already exists in this city");
        }
        Location location = new Location(request.name(), request.city(), request.address(), request.latitude(),
                request.longitude(), request.category(), LocationSource.DIRECTORY, null, user.id());
        if (request.aliases() != null) {
            request.aliases().forEach(location::addAlias);
        }
        return mapper.toResponse(repository.save(location));
    }

    @Transactional
    public LocationResponse addAlias(Long id, String alias, CurrentUser user) {
        user.requireAdmin();
        Location location = findOrThrow(id);
        location.addAlias(alias);
        return mapper.toResponse(repository.save(location));
    }

    /**
     * A member picked a place on the map: return the existing Location for it, or create one.
     * The matcher decides what "the same place" means, so the same café is never stored twice.
     */
    @Transactional
    public ResolveResult resolve(ResolvePlaceRequest request, CurrentUser user) {
        PlaceCandidate candidate = new PlaceCandidate(request.name(), request.city(), request.latitude(),
                request.longitude(), blankToNull(request.externalId()));
        if (candidate.externalId() != null) {
            Optional<Location> byExternalId = repository.findFirstByExternalId(candidate.externalId());
            if (byExternalId.isPresent()) {
                return new ResolveResult(mapper.toResponse(byExternalId.get()), false);
            }
        }
        Optional<Location> sameName = repository.findByNormalizedName(TextNormalizer.normalize(request.name())).stream()
                .filter(existing -> matcher.isSamePlace(candidate, existing))
                .findFirst();
        if (sameName.isPresent()) {
            return new ResolveResult(mapper.toResponse(sameName.get()), false);
        }
        Location created = repository.save(new Location(request.name(), request.city(), request.address(),
                request.latitude(), request.longitude(), request.category(), LocationSource.MAP,
                candidate.externalId(), user.id()));
        return new ResolveResult(mapper.toResponse(created), true);
    }

    public record ResolveResult(LocationResponse location, boolean created) {
    }

    /** Candidates for the whole text and for each word, ranked by the matcher. */
    private List<Location> rank(String query, int size) {
        String term = TextNormalizer.normalize(query);
        if (term.isEmpty()) {
            return List.of();
        }
        Map<Long, Location> candidates = new LinkedHashMap<>();
        repository.findCandidates(term, PageRequest.of(0, CANDIDATE_POOL)).forEach(l -> candidates.put(l.getId(), l));
        if (term.contains(" ")) {
            Arrays.stream(term.split(" "))
                    .filter(word -> word.length() >= MIN_EXTERNAL_QUERY_LENGTH)
                    .forEach(word -> repository.findCandidates(word, PageRequest.of(0, CANDIDATE_POOL))
                            .forEach(l -> candidates.putIfAbsent(l.getId(), l)));
        }
        record Scored(Location location, double score) {
        }
        return candidates.values().stream()
                .map(location -> new Scored(location, matcher.score(query, location)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(scored -> scored.location().getName()))
                .limit(size)
                .map(Scored::location)
                .toList();
    }

    private static PlaceCandidate candidate(GeocodedPlace place) {
        return new PlaceCandidate(place.name(), place.city(), place.latitude(), place.longitude(), place.externalId());
    }

    private Location findOrThrow(Long id) {
        return repository.findWithAliasesById(id)
                .orElseThrow(() -> new EntityNotFoundException("Location not found: " + id));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
