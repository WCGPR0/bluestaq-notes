package com.bluestaq.notesapi.team.dto;

import com.bluestaq.notesapi.team.Team;

import java.time.Instant;

public record TeamResponse(String id, String name, Instant createdAt) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getCreatedAt());
    }
}
