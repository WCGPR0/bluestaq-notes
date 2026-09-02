package com.bluestaq.notesapi.user.dto;

import com.bluestaq.notesapi.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserUpdateRequest(
        String name,
        @Email String email,
        @Size(min = 8) String password,
        Set<Role> roles,
        Set<String> teamIds) {
}
