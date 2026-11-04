package ar.edu.um.catalogo.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.catalogo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catalogo acepta solo JWT de usuario emitidos por turnos-service:
 * firma con su clave, vigentes, con el emisor y la audiencia esperados y con ROLE_USER.
 */
class JwtValidationIntegrationTest extends IntegrationTest {

    private static final String STATUS_URL = "/api/sync/status";

    @Autowired MockMvc mvc;

    @Test
    void validUserTokenIsAccepted() throws Exception {
        mvc.perform(get(STATUS_URL).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.user())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get(STATUS_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        expectUnauthorized(TestTokens.builder().signedWithOtherKey().build());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        expectUnauthorized(TestTokens.builder().expired().build());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        expectUnauthorized(TestTokens.builder().issuer("otro-emisor").build());
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        expectUnauthorized(TestTokens.builder().audience("otra-app").build());
    }

    @Test
    void tokenWithoutRoleUserIsForbidden() throws Exception {
        mvc.perform(get(STATUS_URL).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.builder().authorities("").build())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void regularUserCannotRunAdministrativeSync() throws Exception {
        mvc.perform(post("/api/sync/full").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.user())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/sync/run").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.user())))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanForceAFullSynchronization() throws Exception {
        org.mockito.Mockito.when(catedraClient.fetchSnapshot()).thenReturn(ar.edu.um.catalogo.CatalogFixtures.snapshot(3));
        String admin = TestTokens.builder().authorities("ROLE_USER ROLE_ADMIN").build();

        mvc.perform(post("/api/sync/full").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FULL"))
            .andExpect(jsonPath("$.currentVersion").value(3));
    }

    private void expectUnauthorized(String token) throws Exception {
        mvc.perform(get(STATUS_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
