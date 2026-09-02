package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.team.dto.TeamCreateRequest;
import com.bluestaq.notesapi.team.dto.TeamResponse;
import com.bluestaq.notesapi.team.dto.TeamUpdateRequest;
import com.bluestaq.notesapi.user.Role;
import com.bluestaq.notesapi.user.User;
import com.bluestaq.notesapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamAccessGuard teamAccessGuard;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, userRepository, teamAccessGuard);
    }

    private AuthenticatedUser asRequester(String userId, boolean admin) {
        return new AuthenticatedUser(userId, admin ? Set.of(Role.ADMIN) : Set.of(Role.USER), Set.of());
    }

    private User existingUser(String id, Set<String> teamIds) {
        User user = new User();
        user.setId(id);
        user.setTeamIds(new HashSet<>(teamIds));
        return user;
    }

    private Team existingTeam(String id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return team;
    }

    // ---- create ----

    @Test
    void create_savesNewTeamAndAtomicallyAddsItToRequestersOwnTeamIds() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team toSave = invocation.getArgument(0);
            toSave.setId("team-1");
            return toSave;
        });

        TeamResponse response = teamService.create(requester, new TeamCreateRequest("Engineering"));

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        assertEquals("Engineering", teamCaptor.getValue().getName());
        assertTrue(teamCaptor.getValue().getCreatedAt() != null);

        // Atomic $addToSet (not read-modify-save) so two concurrent team creations by the same user
        // can't clobber each other's membership update.
        verify(userRepository).addTeamMembership("user-1", "team-1");

        assertEquals("team-1", response.id());
        assertEquals("Engineering", response.name());
    }

    // ---- listForRequester ----

    @Test
    void listForRequester_whenAdmin_returnsAllTeamsWithoutScopingToOwnMembership() {
        AuthenticatedUser requester = asRequester("admin-1", true);
        when(teamRepository.findAll()).thenReturn(List.of(existingTeam("team-1", "Engineering"), existingTeam("team-2", "Sales")));

        List<TeamResponse> responses = teamService.listForRequester(requester);

        assertEquals(2, responses.size());
        verify(userRepository, never()).findById(any());
        verify(teamRepository, never()).findAllById(any());
    }

    @Test
    void listForRequester_whenNonAdmin_returnsOnlyRequestersOwnTeams() {
        AuthenticatedUser requester = asRequester("user-1", false);
        User requesterUser = existingUser("user-1", Set.of("team-1"));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(requesterUser));
        when(teamRepository.findAllById(Set.of("team-1"))).thenReturn(List.of(existingTeam("team-1", "Engineering")));

        List<TeamResponse> responses = teamService.listForRequester(requester);

        assertEquals(1, responses.size());
        assertEquals("team-1", responses.get(0).id());
        verify(teamRepository, never()).findAll();
    }

    // ---- getById ----

    @Test
    void getById_whenTeamExistsAndGuardPasses_returnsTeam() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1", "Engineering")));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");

        TeamResponse response = teamService.getById(requester, "team-1");

        assertEquals("team-1", response.id());
        verify(teamAccessGuard).assertMember(requester, "team-1");
    }

    @Test
    void getById_whenTeamNotFound_throwsResourceNotFoundException() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getById(requester, "missing"));

        verify(teamAccessGuard, never()).assertMember(any(), any());
    }

    @Test
    void getById_whenGuardRejects_propagatesForbiddenOperationException() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1", "Engineering")));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(ForbiddenOperationException.class, () -> teamService.getById(requester, "team-1"));
    }

    // ---- update ----

    @Test
    void update_whenTeamExistsAndGuardPasses_renamesAndSavesTeam() {
        AuthenticatedUser requester = asRequester("user-1", false);
        Team team = existingTeam("team-1", "Old Name");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.update(requester, "team-1", new TeamUpdateRequest("New Name"));

        assertEquals("New Name", response.name());
        verify(teamRepository).save(team);
    }

    @Test
    void update_whenTeamNotFound_throwsResourceNotFoundException() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamService.update(requester, "missing", new TeamUpdateRequest("New Name")));

        verify(teamAccessGuard, never()).assertMember(any(), any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void update_whenGuardRejects_propagatesForbiddenOperationExceptionWithoutSaving() {
        AuthenticatedUser requester = asRequester("user-1", false);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1", "Old Name")));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(ForbiddenOperationException.class,
                () -> teamService.update(requester, "team-1", new TeamUpdateRequest("New Name")));

        verify(teamRepository, never()).save(any());
    }
}
