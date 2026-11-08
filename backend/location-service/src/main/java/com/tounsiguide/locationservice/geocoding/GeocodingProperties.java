package com.tounsiguide.locationservice.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings under {@code tounsiguide.geocoding} (see application.properties). */
@ConfigurationProperties("tounsiguide.geocoding")
public record GeocodingProperties(
        String provider,
        String language,
        Duration timeout,
        Duration cacheTtl,
        String userAgent,
        Photon photon
) {

    public GeocodingProperties {
        if (language == null || language.isBlank()) {
            language = "fr";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(4);
        }
        if (cacheTtl == null) {
            cacheTtl = Duration.ofMinutes(10);
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "TounsiGuide/0.2";
        }
        if (photon == null) {
            photon = new Photon(null, null);
        }
    }

    /**
     * @param url  Photon server (public komoot server by default, or a self-hosted one)
     * @param bbox search area as minLon,minLat,maxLon,maxLat (Tunisia by default)
     */
    public record Photon(String url, String bbox) {

        public Photon {
            if (url == null || url.isBlank()) {
                url = "https://photon.komoot.io";
            }
            if (bbox == null || bbox.isBlank()) {
                bbox = "7.5,30.2,11.6,37.6";
            }
        }
    }
}
