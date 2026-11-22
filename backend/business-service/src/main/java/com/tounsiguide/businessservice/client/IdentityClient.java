package com.tounsiguide.businessservice.client;

import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Calls the Identity Service (8083) to give the global role BUSINESS_REP to a member whose page request was
 * approved (S3-B06). The role is added: the member keeps MEMBER and any other role. Being a member of a given
 * page is decided by this service (PageMember), never by the role alone.
 */
@Component
public class IdentityClient {

    private final RestClient restClient;

    public IdentityClient(
            @Value("${tounsiguide.identity.url:http://localhost:8083}") String identityUrl,
            @Value("${tounsiguide.security.internal-token}") String internalToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(identityUrl).requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
    }

    /**
     * Adds BUSINESS_REP to the user's roles (idempotent).
     *
     * @throws ConflictException              when the account no longer exists
     * @throws DependencyUnavailableException when the Identity Service cannot be reached or fails
     */
    public void grantBusinessRep(String userId) {
        try {
            restClient.post().uri("/internal/users/{id}/roles/BUSINESS_REP", userId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ConflictException("Le compte du demandeur n'existe plus");
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("IDENTITY_SERVICE_UNAVAILABLE",
                    "Le service des comptes est temporairement indisponible", exception);
        }
    }
}
