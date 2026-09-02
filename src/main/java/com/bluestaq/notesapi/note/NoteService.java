package com.bluestaq.notesapi.note;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.note.dto.NoteCreateRequest;
import com.bluestaq.notesapi.note.dto.NoteResponse;
import com.bluestaq.notesapi.note.dto.NoteUpdateRequest;
import com.bluestaq.notesapi.team.Team;
import com.bluestaq.notesapi.team.TeamAccessGuard;
import com.bluestaq.notesapi.team.TeamRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final TeamRepository teamRepository;
    private final TeamAccessGuard teamAccessGuard;

    public NoteService(NoteRepository noteRepository, TeamRepository teamRepository, TeamAccessGuard teamAccessGuard) {
        this.noteRepository = noteRepository;
        this.teamRepository = teamRepository;
        this.teamAccessGuard = teamAccessGuard;
    }

    public NoteResponse create(AuthenticatedUser requester, NoteCreateRequest request) {
        findTeamOrThrow(request.teamId());
        teamAccessGuard.assertMember(requester, request.teamId());

        Note note = new Note();
        note.setTitle(request.title());
        note.setBody(request.body());
        note.setTeamId(request.teamId());
        note.setAuthorId(requester.userId());
        note.setArchived(false);
        Instant now = Instant.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);

        return NoteResponse.from(noteRepository.save(note));
    }

    public NoteResponse getById(AuthenticatedUser requester, String noteId) {
        Note note = findNoteOrThrow(noteId);
        teamAccessGuard.assertMember(requester, note.getTeamId());
        return NoteResponse.from(note);
    }

    public List<NoteResponse> listForTeam(AuthenticatedUser requester, String teamId) {
        findTeamOrThrow(teamId);
        teamAccessGuard.assertMember(requester, teamId);
        return noteRepository.findByTeamId(teamId).stream().map(NoteResponse::from).toList();
    }

    public NoteResponse update(AuthenticatedUser requester, String noteId, NoteUpdateRequest request) {
        Note note = findNoteOrThrow(noteId);
        teamAccessGuard.assertMember(requester, note.getTeamId());

        if (request.teamId() != null && !request.teamId().equals(note.getTeamId())) {
            Team destinationTeam = findTeamOrThrow(request.teamId());
            teamAccessGuard.assertMember(requester, destinationTeam.getId());
            note.setTeamId(destinationTeam.getId());
        }

        if (request.title() != null) {
            note.setTitle(request.title());
        }
        if (request.body() != null) {
            note.setBody(request.body());
        }
        if (request.archived() != null) {
            note.setArchived(request.archived());
        }
        note.setUpdatedAt(Instant.now());

        return NoteResponse.from(noteRepository.save(note));
    }

    private Note findNoteOrThrow(String noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + noteId));
    }

    private Team findTeamOrThrow(String teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }
}
