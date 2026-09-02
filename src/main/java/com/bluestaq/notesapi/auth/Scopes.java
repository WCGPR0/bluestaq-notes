package com.bluestaq.notesapi.auth;

public final class Scopes {

    public static final String PROFILE_READ = "profile:read";
    public static final String PROFILE_WRITE = "profile:write";
    public static final String TEAMS_READ = "teams:read";
    public static final String TEAMS_WRITE = "teams:write";
    public static final String NOTES_READ = "notes:read";
    public static final String NOTES_WRITE = "notes:write";

    public static final String ALL = String.join(" ",
            PROFILE_READ, PROFILE_WRITE, TEAMS_READ, TEAMS_WRITE, NOTES_READ, NOTES_WRITE);

    private Scopes() {
    }
}
