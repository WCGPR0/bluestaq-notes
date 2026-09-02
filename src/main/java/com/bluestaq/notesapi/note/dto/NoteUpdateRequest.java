package com.bluestaq.notesapi.note.dto;

public record NoteUpdateRequest(String title, String body, Boolean archived, String teamId) {
}
