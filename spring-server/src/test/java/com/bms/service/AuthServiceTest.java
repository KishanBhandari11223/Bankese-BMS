package com.bms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.dto.UpdateProfileRequest;
import com.bms.dto.VerifyPasswordRequest;
import com.bms.entity.User;
import com.bms.repository.UserRepository;
import com.bms.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private User user;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
        when(userRepository.findByUsernameIgnoreCase("alice"))
                .thenReturn(Optional.of(user));
    }

    @Test
    void updateCurrentUserWritesContactInformationToUserRecord() {
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("alice@example.com");
        when(user.getRole()).thenReturn(User.Role.CUSTOMER);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        authService.updateCurrentUser("alice", new UpdateProfileRequest(
                "Alice Smith", "alice@example.com", " 555-0100 "));

        verify(user).updateContactInformation(
                "Alice Smith", "alice@example.com", "555-0100");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateCurrentUserRejectsAnEmailUsedByAnotherUser() {
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("alice@example.com");
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(
                "other@example.com", 7L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.updateCurrentUser("alice", new UpdateProfileRequest(
                        "Alice Smith", "other@example.com", null)));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void verifyCurrentPasswordChecksTheStoredPasswordHash() {
        when(user.getPasswordHash()).thenReturn("encoded-password");
        when(passwordEncoder.matches("correct-password", "encoded-password"))
                .thenReturn(true);

        authService.verifyCurrentPassword(
                "alice", new VerifyPasswordRequest("correct-password"));

        verify(passwordEncoder).matches("correct-password", "encoded-password");
    }

    @Test
    void verifyCurrentPasswordRejectsAnIncorrectPassword() {
        when(user.getPasswordHash()).thenReturn("encoded-password");
        when(passwordEncoder.matches("incorrect", "encoded-password"))
                .thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.verifyCurrentPassword(
                        "alice", new VerifyPasswordRequest("incorrect")));

        assertEquals(401, error.getStatusCode().value());
    }
}