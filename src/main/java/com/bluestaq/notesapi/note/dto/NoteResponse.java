package com.bluestaq.notesapi.note.dto;

import com.bluestaq.notesapi.note.Note;

import java.time.Instant;

public record NoteResponse(
        String id,
        String title,
        String body,
        String teamId,
        String authorId,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(note.getId(), note.getTitle(), note.getBody(), note.getTeamId(),
                note.getAuthorId(), note.isArchived(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
