package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.team.dto.TeamCreateRequest;
import com.bluestaq.notesapi.team.dto.TeamResponse;
import com.bluestaq.notesapi.team.dto.TeamUpdateRequest;
import com.bluestaq.notesapi.user.User;
import com.bluestaq.notesapi.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamAccessGuard teamAccessGuard;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository, TeamAccessGuard teamAccessGuard) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamAccessGuard = teamAccessGuard;
    }

    public TeamResponse create(AuthenticatedUser requester, TeamCreateRequest request) {
        Team team = new Team();
        team.setName(request.name());
        team.setCreatedAt(Instant.now());
        Team saved = teamRepository.save(team);

        userRepository.addTeamMembership(requester.userId(), saved.getId());

        return TeamResponse.from(saved);
    }

    public List<TeamResponse> listForRequester(AuthenticatedUser requester) {
        User requesterUser = findUserOrThrow(requester.userId());
        return teamRepository.findAllById(requesterUser.getTeamIds()).stream().map(TeamResponse::from).toList();
    }

    public TeamResponse getById(AuthenticatedUser requester, String teamId) {
        Team team = findTeamOrThrow(teamId);
        teamAccessGuard.assertMember(requester, teamId);
        return TeamResponse.from(team);
    }

    public TeamResponse update(AuthenticatedUser requester, String teamId, TeamUpdateRequest request) {
        Team team = findTeamOrThrow(teamId);
        teamAccessGuard.assertMember(requester, teamId);
        team.setName(request.name());
        return TeamResponse.from(teamRepository.save(team));
    }

    private Team findTeamOrThrow(String teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
