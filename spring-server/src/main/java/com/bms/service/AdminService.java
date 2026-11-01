package com.bms.service;

import com.bms.dto.AccountResponse;
import com.bms.dto.AdminCustomerResponse;
import com.bms.dto.UpdateAccountStatusRequest;
import com.bms.dto.UpdateCustomerStatusRequest;
import com.bms.dto.UpdateProfileRequest;
import com.bms.entity.Account;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CurrentAdminService currentAdminService;
    private final AccountService accountService;

    public AdminService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            CurrentAdminService currentAdminService,
            AccountService accountService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.currentAdminService = currentAdminService;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<AdminCustomerResponse> searchCustomers(String admin, String query) {
        currentAdminService.requireAdmin(admin);
        List<User> customers = query == null || query.isBlank()
                ? userRepository.findTop100ByRoleOrderByIdDesc(User.Role.CUSTOMER)
                : userRepository.searchCustomers(User.Role.CUSTOMER, query.trim());
        return customers.stream().map(this::customerResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminCustomerResponse getCustomer(String admin, Long customerId) {
        currentAdminService.requireAdmin(admin);
        return customerResponse(requireCustomer(customerId));
    }

    @Transactional
    public AdminCustomerResponse updateCustomer(
            String admin, Long customerId, UpdateProfileRequest request) {
        currentAdminService.requireAdmin(admin);
        User customer = requireCustomer(customerId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!customer.getEmail().equalsIgnoreCase(email)
                && userRepository.existsByEmailIgnoreCaseAndIdNot(email, customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered");
        }
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : request.phone().trim();
        customer.updateContactInformation(request.fullName().trim(), email, phone);
        return customerResponse(userRepository.saveAndFlush(customer));
    }

    @Transactional
    public AdminCustomerResponse updateCustomerStatus(
            String admin, Long customerId, UpdateCustomerStatusRequest request) {
        currentAdminService.requireAdmin(admin);
        User customer = requireCustomerForUpdate(customerId);
        customer.setStatus(request.status());
        User saved = userRepository.saveAndFlush(customer);
        if (saved.getStatus() == User.Status.ACTIVE
                && accountRepository.countByOwner_Id(saved.getId()) == 0) {
            accountService.createDefaultAccounts(saved);
        }
        return customerResponse(saved);
    }

    @Transactional
    public AdminCustomerResponse approveCustomer(String admin, Long customerId) {
        currentAdminService.requireAdmin(admin);
        User customer = requireCustomerForUpdate(customerId);
        if (customer.getStatus() != User.Status.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Customer is not awaiting approval");
        }
        customer.setStatus(User.Status.ACTIVE);
        User saved = userRepository.saveAndFlush(customer);
        accountService.createDefaultAccounts(saved);
        return customerResponse(saved);
    }

    @Transactional
    public AccountResponse updateAccountStatus(
            String admin, Long customerId, Long accountId,
            UpdateAccountStatusRequest request) {
        currentAdminService.requireAdmin(admin);
        User customer = requireCustomer(customerId);
        if (customer.getStatus() != User.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Approve the customer before activating accounts");
        }
        return accountService.updateAccountStatus(customerId, accountId, request.status());
    }

    private User requireCustomer(Long customerId) {
        return userRepository.findById(customerId)
                .filter(user -> user.getRole() == User.Role.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private User requireCustomerForUpdate(Long customerId) {
        return userRepository.findByIdForUpdate(customerId)
                .filter(user -> user.getRole() == User.Role.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private AdminCustomerResponse customerResponse(User user) {
        List<AccountResponse> accounts = accountRepository
                .findAllByOwner_IdOrderByIdDesc(user.getId())
                .stream().map(AccountResponse::from).toList();
        return new AdminCustomerResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole().name(), user.getStatus().name(), accounts);
    }
}