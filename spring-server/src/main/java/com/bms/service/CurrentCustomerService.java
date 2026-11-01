package com.bms.service;

import com.bms.entity.User;
import com.bms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentCustomerService {

    private final UserRepository userRepository;

    public CurrentCustomerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireCustomer(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .filter(candidate -> candidate.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active customer account required"));
        if (user.getRole() != User.Role.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Customer account required");
        }
        return user;
    }

    @Transactional
    public User requireCustomerForUpdate(String username) {
        User user = userRepository.findByUsernameForUpdate(username)
                .filter(candidate -> candidate.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active customer account required"));
        if (user.getRole() != User.Role.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Customer account required");
        }
        return user;
    }
}