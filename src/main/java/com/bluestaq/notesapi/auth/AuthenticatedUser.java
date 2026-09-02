package com.bluestaq.notesapi.auth;

import com.bluestaq.notesapi.user.Role;

import java.util.Set;

public record AuthenticatedUser(String userId, Set<Role> roles, Set<String> scopes) {

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
