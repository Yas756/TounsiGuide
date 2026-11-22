package com.tounsiguide.businessservice;

import com.tounsiguide.businessservice.client.CommunityClient.PostSnapshot;
import com.tounsiguide.businessservice.client.LocationClient.LocationSnapshot;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.page.BusinessPage;
import com.tounsiguide.businessservice.request.VerificationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.springframework.test.util.ReflectionTestUtils.setField;

/** Shared test objects. */
public final class TestData {

    public static final CurrentUser MEMBER = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));
    public static final CurrentUser OTHER = new CurrentUser("user-2", "Karim", Set.of("MEMBER", "BUSINESS_REP"));
    public static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", Set.of("MEMBER", "ADMIN"));

    private TestData() {
    }

    public static LocationSnapshot location(long id) {
        return new LocationSnapshot(id, "Café des Délices", "Sidi Bou Saïd", "Rue Habib Thameur", "cafe");
    }

    public static VerificationRequest request(long id, long locationId, CurrentUser requester) {
        VerificationRequest request = new VerificationRequest(locationId, requester.id(), requester.displayName(),
                "Café des Délices officiel", "Registre de commerce n° 123", "contact@cafe.tn");
        setField(request, "id", id);
        setField(request, "createdAt", Instant.parse("2026-11-10T10:00:00Z"));
        return request;
    }

    public static BusinessPage page(long id, long locationId) {
        BusinessPage page = new BusinessPage(locationId, "Café des Délices", "Rue Habib Thameur", 1L, "admin-1",
                Instant.parse("2026-11-15T10:00:00Z"));
        setField(page, "id", id);
        setField(page, "createdAt", Instant.parse("2026-11-15T10:00:00Z"));
        setField(page, "updatedAt", Instant.parse("2026-11-15T10:00:00Z"));
        return page;
    }

    public static PostSnapshot post(long id, Instant createdAt) {
        return new PostSnapshot(id, "Post " + id, "author-" + id, "Auteur " + id, 14L, createdAt, createdAt, List.of());
    }
}
