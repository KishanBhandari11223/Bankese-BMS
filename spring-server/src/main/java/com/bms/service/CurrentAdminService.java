package com.bms.service;

import com.bms.entity.User;
import com.bms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentAdminService {

    private final UserRepository userRepository;

    public CurrentAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireAdmin(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .filter(candidate -> candidate.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active administrator account required"));
        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Administrator account required");
        }
        return user;
    }
}