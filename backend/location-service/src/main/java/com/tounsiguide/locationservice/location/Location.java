package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.matching.TextNormalizer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A real place. Posts (Community Service) and, later, business pages refer to it by id. */
@Entity
@Table(name = "locations", indexes = {
        @Index(name = "idx_locations_normalized_name", columnList = "normalized_name"),
        @Index(name = "idx_locations_external_id", columnList = "external_id")
})
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "normalized_city", length = 100)
    private String normalizedCity;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** Kind of place, e.g. cafe, restaurant, museum, beach (OpenStreetMap vocabulary). */
    @Column(name = "category", length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private LocationSource source;

    /** Id at the geocoding provider, e.g. "osm:N123456". Used to avoid storing the same place twice. */
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LocationAlias> aliases = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Location() {
        // Required by JPA.
    }

    public Location(String name, String city, String address, Double latitude, Double longitude,
                    String category, LocationSource source, String externalId, String createdBy) {
        this.name = name.trim();
        this.normalizedName = TextNormalizer.normalize(name);
        this.city = city == null || city.isBlank() ? null : city.trim();
        this.normalizedCity = this.city == null ? null : TextNormalizer.normalize(this.city);
        this.address = address == null || address.isBlank() ? null : address.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category == null || category.isBlank() ? null : category.trim();
        this.source = source;
        this.externalId = externalId == null || externalId.isBlank() ? null : externalId.trim();
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Adds another name for this place. Blank values, duplicates and the name itself are ignored. */
    public void addAlias(String alias) {
        String normalized = TextNormalizer.normalize(alias);
        if (normalized.isEmpty() || normalized.equals(normalizedName)) {
            return;
        }
        boolean known = aliases.stream().anyMatch(a -> a.getNormalizedAlias().equals(normalized));
        if (!known) {
            aliases.add(new LocationAlias(this, alias.trim()));
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getCity() {
        return city;
    }

    public String getNormalizedCity() {
        return normalizedCity;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getCategory() {
        return category;
    }

    public LocationSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public List<LocationAlias> getAliases() {
        return aliases;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        Class<?> otherClass = other instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : other.getClass();
        Class<?> thisClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisClass != otherClass) {
            return false;
        }
        Location location = (Location) other;
        return id != null && Objects.equals(id, location.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Location{id=" + id + ", name='" + name + "', city='" + city + "', source=" + source + '}';
    }
}
