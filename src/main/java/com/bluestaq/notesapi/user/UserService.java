package com.bluestaq.notesapi.user;

import com.bluestaq.notesapi.auth.AuthenticatedUser;
import com.bluestaq.notesapi.exception.DuplicateEmailException;
import com.bluestaq.notesapi.exception.ForbiddenOperationException;
import com.bluestaq.notesapi.exception.ResourceNotFoundException;
import com.bluestaq.notesapi.user.dto.UserRegistrationRequest;
import com.bluestaq.notesapi.user.dto.UserResponse;
import com.bluestaq.notesapi.user.dto.UserUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already in use: " + request.email());
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(Role.USER));
        user.setTeamIds(Set.of());
        user.setCreatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse getById(AuthenticatedUser requester, String targetId) {
        User user = findByIdOrThrow(targetId);
        requireSelfOrAdmin(requester, targetId);
        return UserResponse.from(user);
    }

    public UserResponse update(AuthenticatedUser requester, String targetId, UserUpdateRequest request) {
        User user = findByIdOrThrow(targetId);
        requireSelfOrAdmin(requester, targetId);

        if ((request.roles() != null || request.teamIds() != null) && !requester.isAdmin()) {
            throw new ForbiddenOperationException("Only an admin can change roles or team memberships");
        }

        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateEmailException("Email already in use: " + request.email());
            }
            user.setEmail(request.email());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.roles() != null) {
            user.setRoles(request.roles());
        }
        if (request.teamIds() != null) {
            user.setTeamIds(request.teamIds());
        }

        return UserResponse.from(userRepository.save(user));
    }

    public void delete(AuthenticatedUser requester, String targetId) {
        findByIdOrThrow(targetId);
        requireSelfOrAdmin(requester, targetId);
        userRepository.deleteById(targetId);
    }

    private User findByIdOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private void requireSelfOrAdmin(AuthenticatedUser requester, String targetId) {
        if (!requester.isAdmin() && !requester.userId().equals(targetId)) {
            throw new ForbiddenOperationException("Not authorized to access user " + targetId);
        }
    }
}
