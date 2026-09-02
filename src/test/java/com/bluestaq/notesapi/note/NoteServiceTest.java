package com.bluestaq.notesapi.note;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.exception.PreconditionFailedException;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.note.dto.NoteCreateRequest;
import com.bluestaq.notesapi.note.dto.NoteResponse;
import com.bluestaq.notesapi.note.dto.NoteUpdateRequest;
import com.bluestaq.notesapi.team.Team;
import com.bluestaq.notesapi.team.TeamAccessGuard;
import com.bluestaq.notesapi.team.TeamRepository;
import com.bluestaq.notesapi.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamAccessGuard teamAccessGuard;

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(noteRepository, teamRepository, teamAccessGuard);
    }

    private AuthenticatedUser asRequester(String userId) {
        return new AuthenticatedUser(userId, Set.of(Role.USER), Set.of());
    }

    private Team existingTeam(String id) {
        Team team = new Team();
        team.setId(id);
        team.setName("Team " + id);
        return team;
    }

    private Note existingNote(String id, String teamId, String authorId, boolean archived) {
        Note note = new Note();
        note.setId(id);
        note.setTitle("Title");
        note.setBody("Body");
        note.setTeamId(teamId);
        note.setAuthorId(authorId);
        note.setArchived(archived);
        note.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        note.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        note.setVersion(1L);
        return note;
    }

    // ---- create ----

    @Test
    void create_whenTeamNotFound_throwsResourceNotFoundExceptionAndNeverConsultsGuard() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> noteService.create(requester, new NoteCreateRequest("team-1", "Title", "Body")));

        verify(teamAccessGuard, never()).assertMember(any(), any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void create_whenGuardRejects_propagatesForbiddenOperationExceptionAndDoesNotSave() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1")));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(ForbiddenOperationException.class,
                () -> noteService.create(requester, new NoteCreateRequest("team-1", "Title", "Body")));

        verify(noteRepository, never()).save(any());
    }

    @Test
    void create_whenTeamExistsAndGuardPasses_savesNoteWithAuthorTeamAndTimestampsFromRequest() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1")));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> {
            Note toSave = invocation.getArgument(0);
            toSave.setId("note-1");
            return toSave;
        });

        NoteResponse response = noteService.create(requester, new NoteCreateRequest("team-1", "My Title", "My Body"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        Note saved = captor.getValue();
        assertEquals("My Title", saved.getTitle());
        assertEquals("My Body", saved.getBody());
        assertEquals("team-1", saved.getTeamId());
        assertEquals("user-1", saved.getAuthorId());
        assertFalse(saved.isArchived());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        assertEquals("note-1", response.id());
        assertEquals("team-1", response.teamId());
        assertEquals("user-1", response.authorId());
        assertFalse(response.archived());
    }

    // ---- getById ----

    @Test
    void getById_whenNoteNotFound_throwsResourceNotFoundExceptionAndNeverConsultsGuard() {
        AuthenticatedUser requester = asRequester("user-1");
        when(noteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.getById(requester, "missing"));

        verify(teamAccessGuard, never()).assertMember(any(), any());
    }

    @Test
    void getById_whenGuardRejects_propagatesForbiddenOperationException() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(ForbiddenOperationException.class, () -> noteService.getById(requester, "note-1"));
    }

    @Test
    void getById_whenNoteExistsAndGuardPasses_returnsNote() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");

        NoteResponse response = noteService.getById(requester, "note-1");

        assertEquals("note-1", response.id());
        verify(teamAccessGuard).assertMember(requester, "team-1");
    }

    // ---- listForTeam ----

    @Test
    void listForTeam_whenTeamNotFound_throwsResourceNotFoundException() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.listForTeam(requester, "missing"));

        verify(teamAccessGuard, never()).assertMember(any(), any());
    }

    @Test
    void listForTeam_whenGuardRejects_propagatesForbiddenOperationException() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1")));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(ForbiddenOperationException.class, () -> noteService.listForTeam(requester, "team-1"));
    }

    @Test
    void listForTeam_whenGuardPasses_returnsNotesMappedToResponses() {
        AuthenticatedUser requester = asRequester("user-1");
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(existingTeam("team-1")));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.findByTeamId("team-1")).thenReturn(List.of(
                existingNote("note-1", "team-1", "user-1", false),
                existingNote("note-2", "team-1", "user-2", true)));

        List<NoteResponse> responses = noteService.listForTeam(requester, "team-1");

        assertEquals(2, responses.size());
        assertEquals("note-1", responses.get(0).id());
        assertEquals("note-2", responses.get(1).id());
    }

    // ---- update: not-found / guard propagation ----

    @Test
    void update_whenNoteNotFound_throwsResourceNotFoundExceptionAndNeverConsultsGuard() {
        AuthenticatedUser requester = asRequester("user-1");
        when(noteRepository.findById("missing")).thenReturn(Optional.empty());

        // expectedVersion is deliberately mismatched (note doesn't even exist) to confirm the
        // not-found check happens before any version comparison could occur.
        assertThrows(ResourceNotFoundException.class,
                () -> noteService.update(requester, "missing", new NoteUpdateRequest("New Title", null, null, null), 999L));

        verify(teamAccessGuard, never()).assertMember(any(), any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_whenGuardRejectsSourceTeamMembership_propagatesForbiddenOperationExceptionAndDoesNotSave() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-1");

        // expectedVersion is deliberately mismatched to confirm the guard check happens before the
        // version check, i.e. a non-member gets 403 rather than 412 even with a stale version.
        assertThrows(ForbiddenOperationException.class,
                () -> noteService.update(requester, "note-1", new NoteUpdateRequest("New Title", null, null, null), 999L));

        verify(noteRepository, never()).save(any());
    }

    // ---- update: partial-patch field application ----

    @Test
    void update_appliesOnlyPresentFieldsLeavingAbsentFieldsUnchanged() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest("New Title", null, null, null), 1L);

        assertEquals("New Title", response.title());
        assertEquals("Body", response.body());
        assertFalse(response.archived());
    }

    @Test
    void update_archivesNote_whenArchivedTrueInRequest() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, true, null), 1L);

        assertTrue(response.archived());
    }

    @Test
    void update_unarchivesNote_whenArchivedFalseInRequest() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", true);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, false, null), 1L);

        assertFalse(response.archived());
    }

    @Test
    void update_refreshesUpdatedAtButNeverTouchesCreatedAt() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        Instant originalCreatedAt = note.getCreatedAt();
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest("New Title", null, null, null), 1L);

        assertEquals(originalCreatedAt, response.createdAt());
        assertNotNull(response.updatedAt());
    }

    // ---- update: move-note dual-membership branching ----

    @Test
    void update_whenMovingToNonexistentDestinationTeam_throwsResourceNotFoundExceptionAndDoesNotSave() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(teamRepository.findById("team-missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, null, "team-missing"), 1L));

        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_whenMovingTeams_checksMembershipInBothSourceAndDestinationTeams() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        doNothing().when(teamAccessGuard).assertMember(requester, "team-2");
        when(teamRepository.findById("team-2")).thenReturn(Optional.of(existingTeam("team-2")));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, null, "team-2"), 1L);

        assertEquals("team-2", response.teamId());
        verify(teamAccessGuard).assertMember(requester, "team-1");
        verify(teamAccessGuard).assertMember(requester, "team-2");
    }

    @Test
    void update_whenMovingTeamsAndNotMemberOfDestination_throwsForbiddenOperationExceptionAndDoesNotSave() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        doThrow(new ForbiddenOperationException("not a member"))
                .when(teamAccessGuard).assertMember(requester, "team-2");
        when(teamRepository.findById("team-2")).thenReturn(Optional.of(existingTeam("team-2")));

        assertThrows(ForbiddenOperationException.class,
                () -> noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, null, "team-2"), 1L));

        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_whenRequestTeamIdEqualsCurrentTeamId_isNotTreatedAsMoveAndGuardCheckedOnlyOnce() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = noteService.update(requester, "note-1", new NoteUpdateRequest(null, null, null, "team-1"), 1L);

        assertEquals("team-1", response.teamId());
        verify(teamAccessGuard).assertMember(requester, "team-1");
        verify(teamRepository, never()).findById(any());
    }

    // ---- update: optimistic concurrency control ----

    @Test
    void update_whenExpectedVersionDoesNotMatchCurrentVersion_throwsPreconditionFailedExceptionAndDoesNotSave() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");

        assertThrows(PreconditionFailedException.class,
                () -> noteService.update(requester, "note-1", new NoteUpdateRequest("New Title", null, null, null), 2L));

        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_whenRepositoryThrowsOptimisticLockingFailureOnSave_translatesToPreconditionFailedException() {
        AuthenticatedUser requester = asRequester("user-1");
        Note note = existingNote("note-1", "team-1", "user-2", false);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        doNothing().when(teamAccessGuard).assertMember(requester, "team-1");
        when(noteRepository.save(any(Note.class)))
                .thenThrow(new OptimisticLockingFailureException("concurrent modification"));

        assertThrows(PreconditionFailedException.class,
                () -> noteService.update(requester, "note-1", new NoteUpdateRequest("New Title", null, null, null), 1L));
    }
}
