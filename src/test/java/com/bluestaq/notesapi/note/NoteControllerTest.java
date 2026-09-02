package com.bluestaq.notesapi.note;

import com.bluestaq.notesapi.auth.JwtService;
import com.bluestaq.notesapi.note.dto.NoteResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    // JwtAuthenticationFilter is a @Component, so @WebMvcTest instantiates it regardless of
    // addFilters=false (that flag only skips running filters during requests, not bean creation);
    // it needs a JwtService to construct.
    @MockitoBean
    private JwtService jwtService;

    private NoteResponse sampleResponse(String id) {
        return sampleResponse(id, 1L);
    }

    private NoteResponse sampleResponse(String id, long version) {
        return new NoteResponse(id, "Title", "Body", "team-1", "user-1", false,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), version);
    }

    @Test
    void create_withValidRequest_returns201WithBody() throws Exception {
        when(noteService.create(any(), any())).thenReturn(sampleResponse("note-1"));

        mockMvc.perform(post("/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"team-1","title":"Title","body":"Body"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("note-1"))
                .andExpect(jsonPath("$.teamId").value("team-1"));
    }

    @Test
    void create_withBlankTeamId_returns400() throws Exception {
        mockMvc.perform(post("/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"","title":"Title","body":"Body"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withBlankTitle_returns400() throws Exception {
        mockMvc.perform(post("/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"team-1","title":"","body":"Body"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsNote() throws Exception {
        when(noteService.getById(any(), eq("note-1"))).thenReturn(sampleResponse("note-1"));

        mockMvc.perform(get("/v1/notes/note-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note-1"));
    }

    @Test
    void getById_returnsETagHeaderMatchingResponseVersion() throws Exception {
        when(noteService.getById(any(), eq("note-1"))).thenReturn(sampleResponse("note-1"));

        mockMvc.perform(get("/v1/notes/note-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void update_withValidRequest_returns200WithUpdatedBody() throws Exception {
        when(noteService.update(any(), eq("note-1"), any(), eq(1L))).thenReturn(sampleResponse("note-1"));

        mockMvc.perform(patch("/v1/notes/note-1")
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note-1"));
    }

    @Test
    void update_withoutIfMatchHeader_returns428() throws Exception {
        mockMvc.perform(patch("/v1/notes/note-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().is(428));
    }

    @Test
    void update_withMalformedIfMatchHeader_returns400() throws Exception {
        mockMvc.perform(patch("/v1/notes/note-1")
                        .header("If-Match", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_withValidIfMatchHeader_returns200AndEchoesETagHeader() throws Exception {
        when(noteService.update(any(), eq("note-1"), any(), eq(3L))).thenReturn(sampleResponse("note-1", 3L));

        mockMvc.perform(patch("/v1/notes/note-1")
                        .header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void listForTeam_returnsListOfNotes() throws Exception {
        when(noteService.listForTeam(any(), eq("team-1")))
                .thenReturn(List.of(sampleResponse("note-1"), sampleResponse("note-2")));

        mockMvc.perform(get("/v1/teams/team-1/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
