package com.bluestaq.notesapi.auth;

import java.util.Set;

public record AuthenticatedUser(String userId, Set<String> scopes) {

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
