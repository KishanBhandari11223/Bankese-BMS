package com.bms.service;
import com.bms.dto.LoginRequest;
import com.bms.dto.LoginResponse;
import com.bms.security.JwtService;
import java.util.Optional;
import com.bms.dto.RegisterRequest;
import com.bms.dto.RegisterResponse;
import com.bms.dto.UserProfileResponse;
import com.bms.dto.UpdateProfileRequest;
import com.bms.dto.VerifyPasswordRequest;
import com.bms.entity.User;
import com.bms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService) {
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
            this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identity = request.identity().trim();

        Optional<User> result =
                userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                        identity, identity);

        User user = result
                .filter(u -> passwordEncoder.matches(
                        request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username/email or password"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is not active");
        }

        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }

        @Transactional(readOnly = true)
        public UserProfileResponse getCurrentUser(String username) {
                User user = userRepository.findByUsernameIgnoreCase(username)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                return new UserProfileResponse(
                                user.getId(), user.getUsername(), user.getFullName(),
                                user.getEmail(), user.getPhone(), user.getRole().name());
        }

    @Transactional
    public UserProfileResponse updateCurrentUser(
            String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!user.getEmail().equalsIgnoreCase(email)
                && userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered");
        }
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : request.phone().trim();
        user.updateContactInformation(request.fullName().trim(), email, phone);
        User saved = userRepository.saveAndFlush(user);
        return new UserProfileResponse(
                saved.getId(), saved.getUsername(), saved.getFullName(),
                saved.getEmail(), saved.getPhone(), saved.getRole().name());
    }

        @Transactional(readOnly = true)
        public void verifyCurrentPassword(String username, VerifyPasswordRequest request) {
                User user = userRepository.findByUsernameIgnoreCase(username)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));
                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Invalid password");
                }
        }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered");
        }

        String fullName = request.fullName().trim();
        String phone = request.phone() == null
                || request.phone().isBlank()
                ? null
                : request.phone().trim();

        User user = new User(
                fullName,
                username,
                email,
                passwordEncoder.encode(request.password()),
                phone
        );

        User savedUser = userRepository.saveAndFlush(user);
        return RegisterResponse.from(savedUser);
    }
}