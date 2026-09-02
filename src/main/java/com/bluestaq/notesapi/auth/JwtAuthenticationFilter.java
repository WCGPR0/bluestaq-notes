package com.bluestaq.notesapi.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                AuthenticatedUser authenticatedUser = jwtService.parseToken(token);
                List<GrantedAuthority> authorities = authenticatedUser.scopes().stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .map(GrantedAuthority.class::cast)
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                // Any failure to validate or interpret the token (bad signature, expired, unrecognized role/scope
                // shape, malformed claims) must leave the request unauthenticated, never crash the filter chain.
                // Deliberately broad: this is the boundary between "untrusted token" and "trusted principal".
            }
        }
        filterChain.doFilter(request, response);
    }
}
