package com.tounsiguide.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private JsonNode register(String email) throws Exception {
        String body = """
                {"email":"%s","displayName":"Sara","password":"Password123"}""".formatted(email);
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roles[0]").value("MEMBER"))
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, password);
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void registerThenAccessProtectedProfile() throws Exception {
        JsonNode auth = register("Sara@Example.com");
        String token = auth.get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sara@example.com"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("dup@example.com");
        String body = """
                {"email":"DUP@example.com","displayName":"Autre","password":"Password123"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        String body = """
                {"email":"pas-un-email","displayName":"S","password":"court"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        register("login@example.com");
        String body = """
                {"email":"login@example.com","password":"MauvaisMotDePasse"}""";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotAccessAdminEndpoints() throws Exception {
        String token = register("member@example.com").get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsersAndPromoteMember() throws Exception {
        String adminToken = login("admin@tounsiguide.test", "AdminPassword123").get("accessToken").asText();
        String memberId = register("promote@example.com").get("user").get("id").asText();

        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(put("/api/v1/admin/users/" + memberId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test
    void adminCannotRemoveOwnAdminRole() throws Exception {
        JsonNode admin = login("admin@tounsiguide.test", "AdminPassword123");
        String adminId = admin.get("user").get("id").asText();

        mvc.perform(put("/api/v1/admin/users/" + adminId + "/roles")
                        .header("Authorization", "Bearer " + admin.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MEMBER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesTokenAndReuseIsRejected() throws Exception {
        String oldRefresh = register("refresh@example.com").get("refreshToken").asText();

        MvcResult result = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String newRefresh = om.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();

        // Rejeu de l'ancien token : refusé, et toute la famille de tokens est révoquée
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = register("logout@example.com").get("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
