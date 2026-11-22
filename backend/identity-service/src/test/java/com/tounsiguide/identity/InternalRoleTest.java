package com.tounsiguide.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sprint 3 (S3-B06): the Business Service adds BUSINESS_REP through /internal/**, protected by the internal token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalRoleTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Value("${tounsiguide.security.internal-token}")
    String internalToken;

    private JsonNode register(String email) throws Exception {
        String body = """
                {"email":"%s","displayName":"Rep","password":"Password123"}""".formatted(email);
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void theInternalTokenAddsBusinessRepAndKeepsMemberAndItIsIdempotent() throws Exception {
        String userId = register("rep1@example.com").get("user").get("id").asText();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/users/" + userId + "/roles/BUSINESS_REP").header("X-Internal-Token", internalToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles.length()").value(2))
                    .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                    .andExpect(jsonPath("$.roles[1]").value("BUSINESS_REP"));
        }

        // The role is in the next access token.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rep1@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[1]").value("BUSINESS_REP"));
    }

    @Test
    void withoutOrWithAWrongInternalTokenIs401EvenWithAUserToken() throws Exception {
        JsonNode auth = register("rep2@example.com");
        String userId = auth.get("user").get("id").asText();
        String accessToken = auth.get("accessToken").asText();

        mvc.perform(post("/internal/users/" + userId + "/roles/BUSINESS_REP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/internal/users/" + userId + "/roles/BUSINESS_REP")
                        .header("X-Internal-Token", internalToken + "x")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownUserIs404AndOtherRolesCannotBeGranted() throws Exception {
        mvc.perform(post("/internal/users/does-not-exist/roles/BUSINESS_REP").header("X-Internal-Token", internalToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/users/does-not-exist/roles/ADMIN").header("X-Internal-Token", internalToken))
                .andExpect(status().is4xxClientError());
    }
}
