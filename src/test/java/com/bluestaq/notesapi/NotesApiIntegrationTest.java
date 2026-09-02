package com.bluestaq.notesapi;

import com.bluestaq.notesapi.auth.LoginRequest;
import com.bluestaq.notesapi.auth.LoginResponse;
import com.bluestaq.notesapi.note.dto.NoteCreateRequest;
import com.bluestaq.notesapi.note.dto.NoteResponse;
import com.bluestaq.notesapi.note.dto.NoteUpdateRequest;
import com.bluestaq.notesapi.team.dto.TeamCreateRequest;
import com.bluestaq.notesapi.team.dto.TeamResponse;
import com.bluestaq.notesapi.user.Role;
import com.bluestaq.notesapi.user.dto.UserRegistrationRequest;
import com.bluestaq.notesapi.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-stack acceptance tests: real HTTP calls through the real Spring Security filter chain
 * (SecurityConfig, JwtAuthenticationFilter, CustomUserDetailsService/UserPrincipal) against a real
 * (embedded) MongoDB. Everything below is already covered piecemeal at the unit/@WebMvcTest level;
 * this class exists solely to prove the pieces actually wire together end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class NotesApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String PASSWORD = "password123";

    @Test
    void registerThenLogin_thenBearerTokenAuthenticatesSubsequentRequest() {
        String email = uniqueEmail("ada");
        UserRegistrationRequest registration = new UserRegistrationRequest("Ada Lovelace", email, PASSWORD);

        ResponseEntity<UserResponse> registerResponse =
                restTemplate.postForEntity("/v1/users", registration, UserResponse.class);
        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());
        String userId = registerResponse.getBody().id();

        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/v1/auth/login", new LoginRequest(email, PASSWORD), LoginResponse.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String token = loginResponse.getBody().accessToken();
        assertTrue(token != null && !token.isBlank());

        ResponseEntity<UserResponse> profileResponse = getWithAuth("/v1/users/" + userId, token, UserResponse.class);

        assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
        assertEquals(email, profileResponse.getBody().email());
    }

    @Test
    void register_withClientSuppliedRolesAndTeamId_ignoresThemAndPersistsPlainUser() {
        String email = uniqueEmail("mallory");
        Map<String, Object> maliciousRegistration = new LinkedHashMap<>();
        maliciousRegistration.put("name", "Mallory");
        maliciousRegistration.put("email", email);
        maliciousRegistration.put("password", PASSWORD);
        maliciousRegistration.put("roles", List.of("ADMIN"));
        maliciousRegistration.put("teamId", "some-team-id");

        ResponseEntity<UserResponse> registerResponse =
                restTemplate.postForEntity("/v1/users", maliciousRegistration, UserResponse.class);

        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());
        UserResponse created = registerResponse.getBody();
        assertEquals(Set.of(Role.USER), created.roles());
        assertTrue(created.teamIds().isEmpty());

        // Confirm the persisted record (not just the echoed response) matches, via a fresh login + fetch.
        String token = login(email);
        ResponseEntity<UserResponse> fetched = getWithAuth("/v1/users/" + created.id(), token, UserResponse.class);

        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertEquals(Set.of(Role.USER), fetched.getBody().roles());
        assertTrue(fetched.getBody().teamIds().isEmpty());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401AsProblemJson() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v1/teams/some-team-id", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertTrue(response.getBody().contains("\"status\":401"));
    }

    @Test
    void teamCreation_autoJoinsCreator_andBlocksNonMembersWith403() {
        RegisteredUser owner = registerAndLogin("owner");
        RegisteredUser outsider = registerAndLogin("outsider");

        ResponseEntity<TeamResponse> createResponse =
                postWithAuth("/v1/teams", owner.token(), new TeamCreateRequest("Engineering"), TeamResponse.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String teamId = createResponse.getBody().id();

        ResponseEntity<TeamResponse> ownerReadsTeam = getWithAuth("/v1/teams/" + teamId, owner.token(), TeamResponse.class);
        assertEquals(HttpStatus.OK, ownerReadsTeam.getStatusCode());
        assertEquals("Engineering", ownerReadsTeam.getBody().name());

        ResponseEntity<String> outsiderReadsTeam = getWithAuth("/v1/teams/" + teamId, outsider.token(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, outsiderReadsTeam.getStatusCode());
    }

    @Test
    void note_createArchiveAndMoveBetweenTeams_reflectsAcrossTeamNoteLists() {
        RegisteredUser user = registerAndLogin("noter");

        String teamAId = createTeam(user.token(), "Team A").id();
        String teamBId = createTeam(user.token(), "Team B").id();

        ResponseEntity<NoteResponse> createResponse = postWithAuth(
                "/v1/notes", user.token(), new NoteCreateRequest(teamAId, "Sprint plan", "Draft agenda"), NoteResponse.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String noteId = createResponse.getBody().id();
        assertFalse(createResponse.getBody().archived());
        String eTagAfterCreate = createResponse.getHeaders().getETag();

        ResponseEntity<NoteResponse> archiveResponse = patchWithAuth("/v1/notes/" + noteId, user.token(),
                eTagAfterCreate, new NoteUpdateRequest(null, null, true, null), NoteResponse.class);
        assertEquals(HttpStatus.OK, archiveResponse.getStatusCode());
        assertTrue(archiveResponse.getBody().archived());
        String eTagAfterArchive = archiveResponse.getHeaders().getETag();

        ResponseEntity<NoteResponse> fetchAfterArchive = getWithAuth("/v1/notes/" + noteId, user.token(), NoteResponse.class);
        assertTrue(fetchAfterArchive.getBody().archived());

        ResponseEntity<NoteResponse> moveResponse = patchWithAuth("/v1/notes/" + noteId, user.token(),
                eTagAfterArchive, new NoteUpdateRequest(null, null, null, teamBId), NoteResponse.class);
        assertEquals(HttpStatus.OK, moveResponse.getStatusCode());
        assertEquals(teamBId, moveResponse.getBody().teamId());

        ResponseEntity<NoteResponse[]> teamANotes =
                getWithAuth("/v1/teams/" + teamAId + "/notes", user.token(), NoteResponse[].class);
        ResponseEntity<NoteResponse[]> teamBNotes =
                getWithAuth("/v1/teams/" + teamBId + "/notes", user.token(), NoteResponse[].class);

        assertTrue(List.of(teamANotes.getBody()).stream().noneMatch(n -> n.id().equals(noteId)));
        assertTrue(List.of(teamBNotes.getBody()).stream().anyMatch(n -> n.id().equals(noteId)));
    }

    @Test
    void note_updateWithStaleIfMatch_returns412_thenSucceedsWithCurrentIfMatchAndIncrementsETag() {
        RegisteredUser user = registerAndLogin("etag-user");
        String teamId = createTeam(user.token(), "Team ETag").id();

        ResponseEntity<NoteResponse> createResponse = postWithAuth(
                "/v1/notes", user.token(), new NoteCreateRequest(teamId, "Title", "Body"), NoteResponse.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String noteId = createResponse.getBody().id();

        ResponseEntity<NoteResponse> getResponse = getWithAuth("/v1/notes/" + noteId, user.token(), NoteResponse.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        String currentETag = getResponse.getHeaders().getETag();
        assertTrue(currentETag != null && !currentETag.isBlank());

        ResponseEntity<String> staleUpdateResponse = patchWithAuth("/v1/notes/" + noteId, user.token(),
                "\"999\"", new NoteUpdateRequest("Stale Title", null, null, null), String.class);
        assertEquals(HttpStatus.PRECONDITION_FAILED, staleUpdateResponse.getStatusCode());

        ResponseEntity<NoteResponse> updateResponse = patchWithAuth("/v1/notes/" + noteId, user.token(),
                currentETag, new NoteUpdateRequest("Updated Title", null, null, null), NoteResponse.class);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertEquals("Updated Title", updateResponse.getBody().title());

        String newETag = updateResponse.getHeaders().getETag();
        assertTrue(newETag != null && !newETag.isBlank());
        assertFalse(newETag.equals(currentETag));
    }

    @Test
    void openApiDocs_areReachable_andDeclareBearerAuthScheme() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("bearerAuth"));
    }

    private TeamResponse createTeam(String token, String name) {
        ResponseEntity<TeamResponse> response = postWithAuth("/v1/teams", token, new TeamCreateRequest(name), TeamResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private RegisteredUser registerAndLogin(String namePrefix) {
        String email = uniqueEmail(namePrefix);
        ResponseEntity<UserResponse> registerResponse = restTemplate.postForEntity(
                "/v1/users", new UserRegistrationRequest(namePrefix, email, PASSWORD), UserResponse.class);
        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());

        String token = login(email);
        return new RegisteredUser(registerResponse.getBody().id(), email, token);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/v1/auth/login", new LoginRequest(email, PASSWORD), LoginResponse.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        return loginResponse.getBody().accessToken();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private <T> ResponseEntity<T> getWithAuth(String url, String token, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), responseType);
    }

    private <T> ResponseEntity<T> postWithAuth(String url, String token, Object body, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    private <T> ResponseEntity<T> patchWithAuth(String url, String token, String ifMatch, Object body, Class<T> responseType) {
        HttpHeaders headers = authHeaders(token);
        headers.set(HttpHeaders.IF_MATCH, ifMatch);
        return restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), responseType);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private record RegisteredUser(String userId, String email, String token) {
    }
}
