package com.tounsiguide.businessservice;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditEntryRepository;
import com.tounsiguide.businessservice.client.CommunityClient;
import com.tounsiguide.businessservice.client.IdentityClient;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.InternalTokenFilter;
import com.tounsiguide.businessservice.page.BusinessPageRepository;
import com.tounsiguide.businessservice.page.PageMemberRepository;
import com.tounsiguide.businessservice.request.RequestStatus;
import com.tounsiguide.businessservice.request.VerificationRequestRepository;
import com.tounsiguide.businessservice.wall.WallVisibilityOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tounsiguide.businessservice.TestData.location;
import static com.tounsiguide.businessservice.TestData.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole service over HTTP (filter, controllers, validation, transactions) on an in-memory H2 database, with
 * the Location, Community and Identity services mocked. Covers the Sprint 3 review demo: request, admin approval,
 * wall with older posts, hiding from the wall only.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:business_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "tounsiguide.security.internal-token=" + BusinessFlowIntegrationTest.TOKEN
})
class BusinessFlowIntegrationTest {

    static final String TOKEN = "integration-internal-token";
    private static final long LOCATION = 14L;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InternalTokenFilter internalTokenFilter;
    @Autowired
    private VerificationRequestRepository requests;
    @Autowired
    private BusinessPageRepository pages;
    @Autowired
    private PageMemberRepository members;
    @Autowired
    private WallVisibilityOverrideRepository overrides;
    @Autowired
    private AuditEntryRepository audit;

    @MockitoBean
    private LocationClient locationClient;
    @MockitoBean
    private CommunityClient communityClient;
    @MockitoBean
    private IdentityClient identityClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        audit.deleteAll();
        overrides.deleteAll();
        members.deleteAll();
        pages.deleteAll();
        requests.deleteAll();
        reset(locationClient, communityClient, identityClient);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(internalTokenFilter).build();
        when(locationClient.findLocation(LOCATION)).thenReturn(Optional.of(location(LOCATION)));
        when(locationClient.findLocation(99L)).thenReturn(Optional.empty());
    }

    // ----------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String id, String name,
                                                    String roles) {
        return request.header(InternalTokenFilter.HEADER, TOKEN)
                .header("X-User-Id", id)
                .header("X-User-Name", URLEncoder.encode(name, StandardCharsets.UTF_8))
                .header("X-User-Roles", roles);
    }

    private static MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder request) {
        return as(request, "user-1", "Sarra Ben Ali", "MEMBER");
    }

    private static MockHttpServletRequestBuilder other(MockHttpServletRequestBuilder request) {
        return as(request, "user-2", "Karim", "MEMBER,BUSINESS_REP");
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return as(request, "admin-1", "Admin", "MEMBER,ADMIN");
    }

    private static MockHttpServletRequestBuilder visitor(MockHttpServletRequestBuilder request) {
        return request.header(InternalTokenFilter.HEADER, TOKEN);
    }

    private static long id(MvcResult result, String field) throws Exception {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as(field).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private long submitRequest() throws Exception {
        MvcResult result = mvc.perform(member(post("/api/v1/business-pages/requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationId":14,"proposedName":"Café des Délices","evidence":"Registre de commerce n° 123","contact":"+216 71 000 000"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result, "id");
    }

    private long approvedPage() throws Exception {
        long requestId = submitRequest();
        MvcResult result = mvc.perform(admin(post("/api/v1/business-pages/requests/" + requestId + "/approve")))
                .andExpect(status().isOk())
                .andReturn();
        return id(result, "pageId");
    }

    // ----------------------------------------------------------------- security

    @Test
    void withoutTheInternalTokenEverythingIs401() throws Exception {
        mvc.perform(get("/api/v1/business-pages").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void submittingWithoutAUserIs401() throws Exception {
        mvc.perform(visitor(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":14,\"evidence\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------- request workflow

    @Test
    void theRequestIsValidated() throws Exception {
        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":14,\"evidence\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.evidence").exists());
        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":14,\"evidence\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.evidence").exists());
        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidence\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.locationId").exists());
    }

    @Test
    void anUnknownLocationIs404() throws Exception {
        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":99,\"evidence\":\"Registre\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void theLocationServiceDownIs503() throws Exception {
        when(locationClient.findLocation(15L)).thenThrow(new DependencyUnavailableException(
                "LOCATION_SERVICE_UNAVAILABLE", "Le service des lieux est temporairement indisponible", null));

        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":15,\"evidence\":\"Registre\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOCATION_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void aDuplicatePendingRequestIs409() throws Exception {
        submitRequest();
        mvc.perform(member(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":14,\"evidence\":\"Encore\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theQueueAndTheDecisionsAreAdminOnly() throws Exception {
        long requestId = submitRequest();

        mvc.perform(member(get("/api/v1/business-pages/requests").param("status", "PENDING")))
                .andExpect(status().isForbidden());
        mvc.perform(other(post("/api/v1/business-pages/requests/" + requestId + "/approve")))
                .andExpect(status().isForbidden());
        mvc.perform(member(post("/api/v1/business-pages/requests/" + requestId + "/reject"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"non\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(admin(get("/api/v1/business-pages/requests").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].evidence").value("Registre de commerce n° 123"))
                .andExpect(jsonPath("$.content[0].requesterName").value("Sarra Ben Ali"));
        mvc.perform(member(get("/api/v1/business-pages/requests/mine")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void rejectingNeedsAReasonAndIsRecorded() throws Exception {
        long requestId = submitRequest();

        mvc.perform(admin(post("/api/v1/business-pages/requests/" + requestId + "/reject"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
        mvc.perform(admin(post("/api/v1/business-pages/requests/" + requestId + "/reject"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Preuve insuffisante\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Preuve insuffisante"));

        assertThat(pages.count()).isZero();
        assertThat(audit.findAllByRequestIdOrderByIdAsc(requestId)).extracting("action")
                .containsExactly(AuditAction.REQUEST_SUBMITTED, AuditAction.REQUEST_REJECTED);
        verify(identityClient, never()).grantBusinessRep("user-1");
    }

    @Test
    void approvingLinksThePageToTheExistingLocationAndTheRequesterBecomesOwner() throws Exception {
        long requestId = submitRequest();

        MvcResult result = mvc.perform(admin(post("/api/v1/business-pages/requests/" + requestId + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();
        long pageId = id(result, "pageId");

        verify(identityClient).grantBusinessRep("user-1");
        mvc.perform(member(get("/api/v1/business-pages/" + pageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(14))
                .andExpect(jsonPath("$.location.name").value("Café des Délices"))
                .andExpect(jsonPath("$.member").value(true))
                .andExpect(jsonPath("$.memberRole").value("OWNER"));
        mvc.perform(visitor(get("/api/v1/business-pages/by-location/14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pageId))
                .andExpect(jsonPath("$.member").value(false));
        mvc.perform(visitor(get("/api/v1/business-pages/by-location/15")))
                .andExpect(status().isNotFound());
        mvc.perform(visitor(get("/api/v1/business-pages").param("q", "délices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // A second request for the same Location is refused: the page already exists.
        mvc.perform(other(post("/api/v1/business-pages/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":14,\"evidence\":\"Moi aussi\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void identityDownMakesTheApproval503AndLeavesNoHalfState() throws Exception {
        long requestId = submitRequest();
        doThrow(new DependencyUnavailableException("IDENTITY_SERVICE_UNAVAILABLE",
                "Le service des comptes est temporairement indisponible", null))
                .when(identityClient).grantBusinessRep("user-1");

        mvc.perform(admin(post("/api/v1/business-pages/requests/" + requestId + "/approve")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_SERVICE_UNAVAILABLE"));

        assertThat(pages.count()).isZero();
        assertThat(members.count()).isZero();
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(audit.findAllByRequestIdOrderByIdAsc(requestId)).extracting("action")
                .containsExactly(AuditAction.REQUEST_SUBMITTED);
    }

    // ----------------------------------------------------------------- official information

    @Test
    void onlyMembersEditTheOfficialInformation() throws Exception {
        long pageId = approvedPage();
        String body = """
                {"name":"Café des Délices","description":"Vue sur le golfe","openingHours":"8h-23h",
                 "phone":"+216 71 000 000","email":"contact@cafe.tn","website":"https://cafe.tn","address":"Sidi Bou Saïd"}
                """;

        mvc.perform(other(put("/api/v1/business-pages/" + pageId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(admin(put("/api/v1/business-pages/" + pageId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(member(put("/api/v1/business-pages/" + pageId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"website\":\"cafe.tn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.website").exists());
        mvc.perform(member(put("/api/v1/business-pages/" + pageId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Vue sur le golfe"))
                .andExpect(jsonPath("$.openingHours").value("8h-23h"));
    }

    // ----------------------------------------------------------------- wall

    @Test
    void theWallShowsOlderPostsAndHidingOnlyAffectsThisWall() throws Exception {
        long pageId = approvedPage();
        Instant beforeThePage = Instant.parse("2026-10-01T10:00:00Z");
        when(locationClient.confirmedPostIds(LOCATION)).thenReturn(List.of(3L, 8L));
        when(communityClient.findPublishedPosts(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> post(id, beforeThePage)).toList();
        });

        mvc.perform(visitor(get("/api/v1/business-pages/" + pageId + "/wall")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(8))
                .andExpect(jsonPath("$.content[1].createdAt").value("2026-10-01T10:00:00Z"));

        mvc.perform(other(post("/api/v1/business-pages/" + pageId + "/wall/3/hide")))
                .andExpect(status().isForbidden());
        mvc.perform(member(post("/api/v1/business-pages/" + pageId + "/wall/77/hide")))
                .andExpect(status().isNotFound());
        mvc.perform(member(post("/api/v1/business-pages/" + pageId + "/wall/3/hide"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hors sujet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));

        // Public view and a non-member asking for hidden posts: the post is gone from the wall.
        mvc.perform(visitor(get("/api/v1/business-pages/" + pageId + "/wall").param("includeHidden", "true")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(8));
        mvc.perform(other(get("/api/v1/business-pages/" + pageId + "/wall").param("includeHidden", "true")))
                .andExpect(jsonPath("$.content.length()").value(1));
        // A member asking for hidden posts sees it flagged.
        mvc.perform(member(get("/api/v1/business-pages/" + pageId + "/wall").param("includeHidden", "true")))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].id").value(3))
                .andExpect(jsonPath("$.content[1].hidden").value(true))
                .andExpect(jsonPath("$.content[1].hiddenReason").value("Hors sujet"));

        mvc.perform(member(post("/api/v1/business-pages/" + pageId + "/wall/3/unhide")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false));
        mvc.perform(member(post("/api/v1/business-pages/" + pageId + "/wall/3/unhide")))
                .andExpect(status().isConflict());

        mvc.perform(other(get("/api/v1/business-pages/" + pageId + "/wall/history")))
                .andExpect(status().isForbidden());
        mvc.perform(admin(get("/api/v1/business-pages/" + pageId + "/wall/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("POST_UNHIDDEN"))
                .andExpect(jsonPath("$.content[1].action").value("POST_HIDDEN"))
                .andExpect(jsonPath("$.content[1].reason").value("Hors sujet"));
    }

    @Test
    void theWallAnswers503WhenCommunityIsDown() throws Exception {
        long pageId = approvedPage();
        when(locationClient.confirmedPostIds(LOCATION)).thenReturn(List.of(3L));
        when(communityClient.findPublishedPosts(anyCollection())).thenThrow(new DependencyUnavailableException(
                "COMMUNITY_SERVICE_UNAVAILABLE", "Le service des publications est temporairement indisponible", null));

        mvc.perform(visitor(get("/api/v1/business-pages/" + pageId + "/wall")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMUNITY_SERVICE_UNAVAILABLE"));
    }

    @Test
    void anUnknownPageIs404AndANonNumericId400() throws Exception {
        mvc.perform(visitor(get("/api/v1/business-pages/123456")))
                .andExpect(status().isNotFound());
        mvc.perform(visitor(get("/api/v1/business-pages/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // --- Star ratings (S3-US12)

    @Test
    void membersRateAPageFromOneToFiveButNotTheirOwnAndTheListShowsTheAverage() throws Exception {
        long pageId = approvedPage(); // user-1 (Sarra) owns it
        String url = "/api/v1/business-pages/" + pageId + "/rating";

        mvc.perform(visitor(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":5}")))
                .andExpect(status().isUnauthorized());
        mvc.perform(member(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":5}")))
                .andExpect(status().isForbidden());
        mvc.perform(other(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":6}")))
                .andExpect(status().isBadRequest());

        mvc.perform(other(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":4}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.myStars").value(4));
        mvc.perform(admin(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":5}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(4.5))
                .andExpect(jsonPath("$.distribution[3]").value(1))
                .andExpect(jsonPath("$.distribution[4]").value(1));
        // Rating again replaces the stars.
        mvc.perform(other(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"stars\":2}")))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.average").value(3.5));

        mvc.perform(visitor(get(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStars").doesNotExist())
                .andExpect(jsonPath("$.canRate").value(false));
        mvc.perform(visitor(get("/api/v1/business-pages")))
                .andExpect(jsonPath("$.content[0].ratingAverage").value(3.5))
                .andExpect(jsonPath("$.content[0].ratingCount").value(2));

        mvc.perform(other(delete(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.myStars").doesNotExist());
    }
}
