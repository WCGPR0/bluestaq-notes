package com.bluestaq.notesapi.team.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamUpdateRequest(@NotBlank String name) {
}
