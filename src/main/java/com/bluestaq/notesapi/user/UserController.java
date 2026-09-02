package com.bluestaq.notesapi.user;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.auth.Scopes;
import com.bluestaq.notesapi.user.dto.UserRegistrationRequest;
import com.bluestaq.notesapi.user.dto.UserResponse;
import com.bluestaq.notesapi.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.PROFILE_READ + "')")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse getById(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String id) {
        return userService.getById(requester, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.PROFILE_WRITE + "')")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String id,
                                @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(requester, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_" + Scopes.PROFILE_WRITE + "')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser requester, @PathVariable String id) {
        userService.delete(requester, id);
        return ResponseEntity.noContent().build();
    }
}
