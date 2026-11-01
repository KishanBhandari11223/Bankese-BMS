package com.bms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.dto.AccountResponse;
import com.bms.dto.CreateAccountRequest;
import com.bms.entity.Account;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CurrentCustomerService currentCustomerService;
    @Mock
    private User customer;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, currentCustomerService);
    }

    @Test
    void createAssignsAccountToPrincipalResolvedCustomer() {
        when(currentCustomerService.requireCustomerForUpdate("alice")).thenReturn(customer);
        when(customer.getId()).thenReturn(42L);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.create(
                "alice", new CreateAccountRequest(Account.Type.SAVINGS));

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(account.capture());
        assertSame(customer, account.getValue().getOwner());
        assertEquals(Account.Type.SAVINGS, account.getValue().getAccountType());
        assertEquals(Account.Status.PENDING_APPROVAL, account.getValue().getStatus());
        assertEquals("INR", response.currency());
        assertEquals(16, response.accountNumber().length());
    }

    @Test
    void createRejectsAnotherRequestWhileApprovalIsPending() {
        when(currentCustomerService.requireCustomerForUpdate("alice")).thenReturn(customer);
        when(customer.getId()).thenReturn(42L);
        when(accountRepository.existsByOwner_IdAndStatus(
                42L, Account.Status.PENDING_APPROVAL)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> accountService.create(
                        "alice", new CreateAccountRequest(Account.Type.SAVINGS)));

        assertEquals(409, error.getStatusCode().value());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

        @Test
        void customerApprovalCreatesOneActiveSavingsAndCurrentAccount() {
        when(customer.getId()).thenReturn(42L);
        when(accountRepository.countByOwner_Id(42L)).thenReturn(0L);
        when(accountRepository.saveAllAndFlush(any())).thenAnswer(
            invocation -> invocation.getArgument(0));

        List<AccountResponse> created = accountService.createDefaultAccounts(customer);

        assertEquals(2, created.size());
        assertEquals(List.of("CURRENT", "SAVINGS"), created.stream()
            .map(AccountResponse::accountType).sorted().toList());
        assertEquals(List.of("ACTIVE", "ACTIVE"), created.stream()
            .map(AccountResponse::status).toList());
        }

    @Test
    void listQueriesOnlyTheCustomerResolvedFromPrincipal() {
        when(currentCustomerService.requireCustomer("alice")).thenReturn(customer);
        when(customer.getId()).thenReturn(42L);
        when(accountRepository.findAllByOwner_IdOrderByIdDesc(42L))
                .thenReturn(List.of());

        accountService.list("alice");

        verify(currentCustomerService).requireCustomer("alice");
        verify(accountRepository).findAllByOwner_IdOrderByIdDesc(42L);
    }
}