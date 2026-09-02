package com.bluestaq.notesapi.user.dto;

import com.bluestaq.notesapi.user.Role;
import com.bluestaq.notesapi.user.User;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String id,
        String name,
        String email,
        Set<Role> roles,
        Set<String> teamIds,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRoles(), user.getTeamIds(), user.getCreatedAt());
    }
}
