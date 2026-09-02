package com.bluestaq.notesapi.user;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.DuplicateEmailException;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.user.dto.UserRegistrationRequest;
import com.bluestaq.notesapi.user.dto.UserResponse;
import com.bluestaq.notesapi.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    private User existingUser(String id, String email, Set<String> teamIds) {
        User user = new User();
        user.setId(id);
        user.setName("Existing Name");
        user.setEmail(email);
        user.setPasswordHash("old-hash");
        user.setTeamIds(teamIds);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private AuthenticatedUser asRequester(String userId) {
        return new AuthenticatedUser(userId, Set.of());
    }

    // ---- register ----

    @Test
    void register_persistsUserWithHashedPasswordAndNoTeams() {
        UserRegistrationRequest request = new UserRegistrationRequest("Ada", "ada@example.com", "password123");
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId("generated-id");
            return toSave;
        });

        UserResponse response = userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();
        assertEquals("Ada", persisted.getName());
        assertEquals("ada@example.com", persisted.getEmail());
        assertEquals("hashed-password", persisted.getPasswordHash());
        assertTrue(persisted.getTeamIds() == null || persisted.getTeamIds().isEmpty());

        assertEquals("generated-id", response.id());
        assertEquals("ada@example.com", response.email());
    }

    @Test
    void register_whenEmailAlreadyExists_throwsDuplicateEmailException() {
        UserRegistrationRequest request = new UserRegistrationRequest("Ada", "ada@example.com", "password123");
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.register(request));

        verify(userRepository, never()).save(any());
    }

    // ---- getById ----

    @Test
    void getById_whenRequesterIsSelf_returnsUser() {
        User user = existingUser("id-1", "one@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(asRequester("id-1"), "id-1");

        assertEquals("id-1", response.id());
        assertEquals("one@example.com", response.email());
    }

    @Test
    void getById_whenRequesterIsNotSelf_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "one@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenOperationException.class,
                () -> userService.getById(asRequester("id-2"), "id-1"));
    }

    @Test
    void getById_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getById(asRequester("missing"), "missing"));
    }

    // ---- update ----

    @Test
    void update_whenSelfUpdatesOwnNameEmailAndPassword_updatesFieldsAndHashesPassword() {
        User user = existingUser("id-1", "old@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest("New Name", "new@example.com", "newpassword");
        UserResponse response = userService.update(asRequester("id-1"), "id-1", request);

        assertEquals("New Name", response.name());
        assertEquals("new@example.com", response.email());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void update_withNullFields_leavesExistingValuesUnchanged() {
        User user = existingUser("id-1", "old@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest(null, null, null);
        UserResponse response = userService.update(asRequester("id-1"), "id-1", request);

        assertEquals("Existing Name", response.name());
        assertEquals("old@example.com", response.email());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void update_whenRequesterIsNotSelf_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "old@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserUpdateRequest request = new UserUpdateRequest("New Name", null, null);

        assertThrows(ForbiddenOperationException.class,
                () -> userService.update(asRequester("id-2"), "id-1", request));
    }

    @Test
    void update_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        UserUpdateRequest request = new UserUpdateRequest("New Name", null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> userService.update(asRequester("missing"), "missing", request));
    }

    @Test
    void update_whenChangingEmailToOneUsedByAnotherUser_throwsDuplicateEmailException() {
        User user = existingUser("id-1", "old@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        UserUpdateRequest request = new UserUpdateRequest(null, "taken@example.com", null);

        assertThrows(DuplicateEmailException.class,
                () -> userService.update(asRequester("id-1"), "id-1", request));

        verify(userRepository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void delete_whenRequesterIsSelf_deletesUser() {
        User user = existingUser("id-1", "one@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        userService.delete(asRequester("id-1"), "id-1");

        verify(userRepository).deleteById("id-1");
    }

    @Test
    void delete_whenRequesterIsNotSelf_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "one@example.com", Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenOperationException.class,
                () -> userService.delete(asRequester("id-2"), "id-1"));

        verify(userRepository, never()).deleteById(anyString());
    }

    @Test
    void delete_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.delete(asRequester("missing"), "missing"));
    }
}
