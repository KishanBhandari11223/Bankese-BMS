package com.bms.controller;

import com.bms.dto.AccountResponse;
import com.bms.dto.AdminCustomerResponse;
import com.bms.dto.UpdateAccountStatusRequest;
import com.bms.dto.UpdateCustomerStatusRequest;
import com.bms.dto.UpdateProfileRequest;
import com.bms.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/customers")
    public List<AdminCustomerResponse> searchCustomers(
            @AuthenticationPrincipal String admin,
            @RequestParam(required = false) String query) {
        return adminService.searchCustomers(admin, query);
    }

    @GetMapping("/customers/{customerId}")
    public AdminCustomerResponse getCustomer(
            @AuthenticationPrincipal String admin,
            @PathVariable Long customerId) {
        return adminService.getCustomer(admin, customerId);
    }

    @PutMapping("/customers/{customerId}")
    public AdminCustomerResponse updateCustomer(
            @AuthenticationPrincipal String admin,
            @PathVariable Long customerId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return adminService.updateCustomer(admin, customerId, request);
    }

    @PutMapping("/customers/{customerId}/status")
    public AdminCustomerResponse updateCustomerStatus(
            @AuthenticationPrincipal String admin,
            @PathVariable Long customerId,
            @Valid @RequestBody UpdateCustomerStatusRequest request) {
        return adminService.updateCustomerStatus(admin, customerId, request);
    }

    @PostMapping("/customers/{customerId}/approve")
    public AdminCustomerResponse approveCustomer(
            @AuthenticationPrincipal String admin,
            @PathVariable Long customerId) {
        return adminService.approveCustomer(admin, customerId);
    }

    @PutMapping("/customers/{customerId}/accounts/{accountId}/status")
    public AccountResponse updateAccountStatus(
            @AuthenticationPrincipal String admin,
            @PathVariable Long customerId,
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return adminService.updateAccountStatus(
                admin, customerId, accountId, request);
    }
}