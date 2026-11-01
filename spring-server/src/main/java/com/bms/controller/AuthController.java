package com.bms.controller;
import com.bms.dto.LoginRequest;
import com.bms.dto.LoginResponse;
import com.bms.dto.RegisterRequest;
import com.bms.dto.RegisterResponse;
import com.bms.dto.UserProfileResponse;
import com.bms.dto.UpdateProfileRequest;
import com.bms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public UserProfileResponse me(
            @AuthenticationPrincipal String username) {

        return authService.getCurrentUser(username);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMe(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateCurrentUser(username, request);
    }

}