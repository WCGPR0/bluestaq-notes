package com.bluestaq.notesapi.user.dto;

import com.bluestaq.notesapi.user.User;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String id,
        String name,
        String email,
        Set<String> teamIds,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getTeamIds(), user.getCreatedAt());
    }
}
