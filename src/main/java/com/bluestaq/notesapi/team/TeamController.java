package com.bluestaq.notesapi.team;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.auth.Scopes;
import com.bluestaq.notesapi.team.dto.TeamCreateRequest;
import com.bluestaq.notesapi.team.dto.TeamResponse;
import com.bluestaq.notesapi.team.dto.TeamUpdateRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/teams")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.TEAMS_WRITE + "')")
    public ResponseEntity<TeamResponse> create(@AuthenticationPrincipal AuthenticatedUser requester,
                                                @Valid @RequestBody TeamCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.create(requester, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.TEAMS_READ + "')")
    public List<TeamResponse> list(@AuthenticationPrincipal AuthenticatedUser requester) {
        return teamService.listForRequester(requester);
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.TEAMS_READ + "')")
    public TeamResponse getById(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String teamId) {
        return teamService.getById(requester, teamId);
    }

    @PatchMapping("/{teamId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.TEAMS_WRITE + "')")
    public TeamResponse update(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String teamId,
                                @Valid @RequestBody TeamUpdateRequest request) {
        return teamService.update(requester, teamId, request);
    }
}
