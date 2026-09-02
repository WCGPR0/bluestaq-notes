package com.bluestaq.notesapi.user;

import com.bluestaq.notesapi.auth.JwtService;
import com.bluestaq.notesapi.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // JwtAuthenticationFilter is a @Component, so @WebMvcTest instantiates it regardless of
    // addFilters=false (that flag only skips running filters during requests, not bean creation);
    // it needs a JwtService to construct.
    @MockitoBean
    private JwtService jwtService;

    private UserResponse sampleResponse(String id) {
        return new UserResponse(id, "Ada Lovelace", "ada@example.com",
                Set.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void register_withValidRequest_returns201WithBody() throws Exception {
        when(userService.register(any())).thenReturn(sampleResponse("id-1"));

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Lovelace","email":"ada@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @ParameterizedTest
    @CsvSource({
            "'', ada@example.com, password123",
            "Ada, not-an-email, password123",
            "Ada, ada@example.com, short"
    })
    void register_withInvalidRequest_returns400(String name, String email, String password) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsUser() throws Exception {
        when(userService.getById(any(), eq("id-1"))).thenReturn(sampleResponse("id-1"));

        mockMvc.perform(get("/v1/users/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"));
    }

    @Test
    void update_withValidRequest_returns200WithUpdatedBody() throws Exception {
        when(userService.update(any(), eq("id-1"), any())).thenReturn(sampleResponse("id-1"));

        mockMvc.perform(patch("/v1/users/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"));
    }

    @Test
    void update_withInvalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(patch("/v1/users/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/v1/users/id-1"))
                .andExpect(status().isNoContent());
    }
}
