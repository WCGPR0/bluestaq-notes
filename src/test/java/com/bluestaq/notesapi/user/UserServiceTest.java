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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
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

    private User existingUser(String id, String email, Set<Role> roles, Set<String> teamIds) {
        User user = new User();
        user.setId(id);
        user.setName("Existing Name");
        user.setEmail(email);
        user.setPasswordHash("old-hash");
        user.setRoles(roles);
        user.setTeamIds(teamIds);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private AuthenticatedUser asRequester(String userId, boolean admin) {
        return new AuthenticatedUser(userId, admin ? Set.of(Role.ADMIN) : Set.of(Role.USER), Set.of());
    }

    // ---- register ----

    @Test
    void register_persistsUserWithHashedPasswordDefaultRoleAndNoTeams() {
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
        assertEquals(Set.of(Role.USER), persisted.getRoles());
        assertTrue(persisted.getTeamIds() == null || persisted.getTeamIds().isEmpty());

        assertEquals("generated-id", response.id());
        assertEquals("ada@example.com", response.email());
        assertEquals(Set.of(Role.USER), response.roles());
    }

    @Test
    void register_whenEmailAlreadyExists_throwsDuplicateEmailException() {
        UserRegistrationRequest request = new UserRegistrationRequest("Ada", "ada@example.com", "password123");
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.register(request));

        verify(userRepository, never()).save(any());
    }

    // ---- listAll ----

    @Test
    void listAll_returnsAllUsersMappedToResponses() {
        User user1 = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        User user2 = existingUser("id-2", "two@example.com", Set.of(Role.ADMIN), Set.of("team-1"));
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<UserResponse> responses = userService.listAll();

        assertEquals(2, responses.size());
        assertEquals(Set.of("id-1", "id-2"), Set.of(responses.get(0).id(), responses.get(1).id()));
    }

    // ---- getById ----

    @Test
    void getById_whenRequesterIsSelf_returnsUser() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(asRequester("id-1", false), "id-1");

        assertEquals("id-1", response.id());
        assertEquals("one@example.com", response.email());
    }

    @Test
    void getById_whenRequesterIsAdminAndNotSelf_returnsUser() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(asRequester("admin-1", true), "id-1");

        assertEquals("id-1", response.id());
    }

    @Test
    void getById_whenRequesterIsNeitherSelfNorAdmin_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenOperationException.class,
                () -> userService.getById(asRequester("id-2", false), "id-1"));
    }

    @Test
    void getById_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getById(asRequester("missing", true), "missing"));
    }

    // ---- update ----

    @Test
    void update_whenSelfUpdatesOwnNameEmailAndPassword_updatesFieldsAndHashesPassword() {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest("New Name", "new@example.com", "newpassword", null, null);
        UserResponse response = userService.update(asRequester("id-1", false), "id-1", request);

        assertEquals("New Name", response.name());
        assertEquals("new@example.com", response.email());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void update_withNullFields_leavesExistingValuesUnchanged() {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, null);
        UserResponse response = userService.update(asRequester("id-1", false), "id-1", request);

        assertEquals("Existing Name", response.name());
        assertEquals("old@example.com", response.email());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void update_whenNonAdminSuppliesRolesOrTeamIds_throwsForbiddenOperationEvenForSelf(boolean rolesOnly) {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserUpdateRequest request = rolesOnly
                ? new UserUpdateRequest(null, null, null, Set.of(Role.ADMIN), null)
                : new UserUpdateRequest(null, null, null, null, Set.of("team-1"));

        assertThrows(ForbiddenOperationException.class,
                () -> userService.update(asRequester("id-1", false), "id-1", request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_whenAdminChangesRolesAndTeamIds_succeeds() {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest(null, null, null, Set.of(Role.ADMIN), Set.of("team-1"));
        UserResponse response = userService.update(asRequester("admin-1", true), "id-1", request);

        assertEquals(Set.of(Role.ADMIN), response.roles());
        assertEquals(Set.of("team-1"), response.teamIds());
    }

    @Test
    void update_whenRequesterIsNeitherSelfNorAdmin_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        UserUpdateRequest request = new UserUpdateRequest("New Name", null, null, null, null);

        assertThrows(ForbiddenOperationException.class,
                () -> userService.update(asRequester("id-2", false), "id-1", request));
    }

    @Test
    void update_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        UserUpdateRequest request = new UserUpdateRequest("New Name", null, null, null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> userService.update(asRequester("missing", true), "missing", request));
    }

    @Test
    void update_whenChangingEmailToOneUsedByAnotherUser_throwsDuplicateEmailException() {
        User user = existingUser("id-1", "old@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        UserUpdateRequest request = new UserUpdateRequest(null, "taken@example.com", null, null, null);

        assertThrows(DuplicateEmailException.class,
                () -> userService.update(asRequester("id-1", false), "id-1", request));

        verify(userRepository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void delete_whenRequesterIsSelf_deletesUser() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        userService.delete(asRequester("id-1", false), "id-1");

        verify(userRepository).deleteById("id-1");
    }

    @Test
    void delete_whenRequesterIsAdmin_deletesUser() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        userService.delete(asRequester("admin-1", true), "id-1");

        verify(userRepository).deleteById("id-1");
    }

    @Test
    void delete_whenRequesterIsNeitherSelfNorAdmin_throwsForbiddenOperationException() {
        User user = existingUser("id-1", "one@example.com", Set.of(Role.USER), Set.of());
        when(userRepository.findById("id-1")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenOperationException.class,
                () -> userService.delete(asRequester("id-2", false), "id-1"));

        verify(userRepository, never()).deleteById(anyString());
    }

    @Test
    void delete_whenTargetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.delete(asRequester("missing", true), "missing"));
    }
}
