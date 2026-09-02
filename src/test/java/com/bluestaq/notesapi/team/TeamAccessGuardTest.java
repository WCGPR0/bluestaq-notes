package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.user.User;
import com.bluestaq.notesapi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamAccessGuardTest {

    @Mock
    private UserRepository userRepository;

    private TeamAccessGuard teamAccessGuard;

    private AuthenticatedUser asRequester(String userId) {
        return new AuthenticatedUser(userId, Set.of());
    }

    private User userWithTeams(String id, Set<String> teamIds) {
        User user = new User();
        user.setId(id);
        user.setTeamIds(teamIds);
        return user;
    }

    @Test
    void assertMember_whenRequesterIsMemberOfTeam_doesNotThrow() {
        teamAccessGuard = new TeamAccessGuard(userRepository);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithTeams("user-1", Set.of("team-1"))));

        assertDoesNotThrow(() -> teamAccessGuard.assertMember(asRequester("user-1"), "team-1"));
    }

    @Test
    void assertMember_whenRequesterIsNotMemberOfTeam_throwsForbiddenOperationException() {
        teamAccessGuard = new TeamAccessGuard(userRepository);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithTeams("user-1", Set.of("team-2"))));

        assertThrows(ForbiddenOperationException.class,
                () -> teamAccessGuard.assertMember(asRequester("user-1"), "team-1"));
    }

    @Test
    void assertMember_reFetchesMembershipOnEveryCall_ratherThanCachingStaleState() {
        teamAccessGuard = new TeamAccessGuard(userRepository);
        AuthenticatedUser requester = asRequester("user-1");
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(userWithTeams("user-1", Set.of("team-1"))))
                .thenReturn(Optional.of(userWithTeams("user-1", Set.of())));

        assertDoesNotThrow(() -> teamAccessGuard.assertMember(requester, "team-1"));
        assertThrows(ForbiddenOperationException.class, () -> teamAccessGuard.assertMember(requester, "team-1"));

        verify(userRepository, times(2)).findById("user-1");
    }
}
