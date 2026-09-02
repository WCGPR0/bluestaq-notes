package com.bluestaq.notesapi.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
