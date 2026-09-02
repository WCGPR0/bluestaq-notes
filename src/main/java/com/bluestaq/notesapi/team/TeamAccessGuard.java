package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.user.User;
import com.bluestaq.notesapi.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class TeamAccessGuard {

    private final UserRepository userRepository;

    public TeamAccessGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void assertMember(AuthenticatedUser requester, String teamId) {
        if (requester.isAdmin()) {
            return;
        }
        User user = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ForbiddenOperationException("Not a member of team " + teamId));
        if (!user.getTeamIds().contains(teamId)) {
            throw new ForbiddenOperationException("Not a member of team " + teamId);
        }
    }
}
