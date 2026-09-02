package com.bluestaq.notesapi.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        String name,
        @Email String email,
        @Size(min = 8) String password) {
}
