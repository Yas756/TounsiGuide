package com.tounsiguide.locationservice.location;

import com.tounsiguide.locationservice.matching.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Another name people use for a Location ("Bardo" for "Musée national du Bardo"). */
@Entity
@Table(name = "location_aliases", indexes = {
        @Index(name = "idx_location_aliases_normalized", columnList = "normalized_alias")
})
public class LocationAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "alias", nullable = false, length = 150)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 150)
    private String normalizedAlias;

    protected LocationAlias() {
        // Required by JPA.
    }

    LocationAlias(Location location, String alias) {
        this.location = location;
        this.alias = alias;
        this.normalizedAlias = TextNormalizer.normalize(alias);
    }

    public Long getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getAlias() {
        return alias;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }
}
