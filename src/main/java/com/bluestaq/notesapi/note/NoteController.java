package com.bluestaq.notesapi.note;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.auth.Scopes;
import com.bluestaq.notesapi.note.dto.NoteCreateRequest;
import com.bluestaq.notesapi.note.dto.NoteResponse;
import com.bluestaq.notesapi.note.dto.NoteUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping("/v1/notes")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_WRITE + "')")
    public ResponseEntity<NoteResponse> create(@AuthenticationPrincipal AuthenticatedUser requester,
                                                @Valid @RequestBody NoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(requester, request));
    }

    @GetMapping("/v1/notes/{noteId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_READ + "')")
    public NoteResponse getById(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String noteId) {
        return noteService.getById(requester, noteId);
    }

    @PatchMapping("/v1/notes/{noteId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_WRITE + "')")
    public NoteResponse update(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String noteId,
                                @Valid @RequestBody NoteUpdateRequest request) {
        return noteService.update(requester, noteId, request);
    }

    @GetMapping("/v1/teams/{teamId}/notes")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_READ + "')")
    public List<NoteResponse> listForTeam(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String teamId) {
        return noteService.listForTeam(requester, teamId);
    }
}
