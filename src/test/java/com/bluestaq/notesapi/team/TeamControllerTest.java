package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.JwtService;
import com.bluestaq.notesapi.team.dto.TeamResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    // JwtAuthenticationFilter is a @Component, so @WebMvcTest instantiates it regardless of
    // addFilters=false (that flag only skips running filters during requests, not bean creation);
    // it needs a JwtService to construct.
    @MockitoBean
    private JwtService jwtService;

    private TeamResponse sampleResponse(String id) {
        return new TeamResponse(id, "Engineering", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void create_withValidRequest_returns201WithBody() throws Exception {
        when(teamService.create(any(), any())).thenReturn(sampleResponse("team-1"));

        mockMvc.perform(post("/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Engineering"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("team-1"))
                .andExpect(jsonPath("$.name").value("Engineering"));
    }

    @Test
    void create_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsListOfTeams() throws Exception {
        when(teamService.listForRequester(any())).thenReturn(List.of(sampleResponse("team-1"), sampleResponse("team-2")));

        mockMvc.perform(get("/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getById_returnsTeam() throws Exception {
        when(teamService.getById(any(), eq("team-1"))).thenReturn(sampleResponse("team-1"));

        mockMvc.perform(get("/v1/teams/team-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("team-1"));
    }

    @Test
    void update_withValidRequest_returns200WithUpdatedBody() throws Exception {
        when(teamService.update(any(), eq("team-1"), any())).thenReturn(sampleResponse("team-1"));

        mockMvc.perform(patch("/v1/teams/team-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("team-1"));
    }

    @Test
    void update_withBlankName_returns400() throws Exception {
        mockMvc.perform(patch("/v1/teams/team-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
