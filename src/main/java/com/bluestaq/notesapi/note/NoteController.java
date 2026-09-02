package com.bluestaq.notesapi.note;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.auth.Scopes;
import com.bluestaq.notesapi.exception.PreconditionRequiredException;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
        NoteResponse response = noteService.create(requester, request);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(String.valueOf(response.version())).body(response);
    }

    @GetMapping("/v1/notes/{noteId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_READ + "')")
    public ResponseEntity<NoteResponse> getById(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String noteId) {
        NoteResponse response = noteService.getById(requester, noteId);
        return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(response);
    }

    @PatchMapping("/v1/notes/{noteId}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_WRITE + "')")
    public ResponseEntity<NoteResponse> update(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String noteId,
                                                @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                @Valid @RequestBody NoteUpdateRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header is required to update a note");
        }
        long expectedVersion = parseETag(ifMatch);
        NoteResponse response = noteService.update(requester, noteId, request, expectedVersion);
        return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(response);
    }

    private long parseETag(String ifMatch) {
        String trimmed = ifMatch.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match header must be a quoted note version, e.g. \"3\"");
        }
    }

    @GetMapping("/v1/teams/{teamId}/notes")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.NOTES_READ + "')")
    public List<NoteResponse> listForTeam(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String teamId) {
        return noteService.listForTeam(requester, teamId);
    }
}
