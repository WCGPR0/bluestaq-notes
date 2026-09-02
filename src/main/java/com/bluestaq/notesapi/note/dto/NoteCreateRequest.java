package com.bluestaq.notesapi.note.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteCreateRequest(
        @NotBlank String teamId,
        @NotBlank String title,
        String body) {
}
