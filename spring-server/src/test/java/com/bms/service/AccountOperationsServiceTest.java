package com.bms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.dto.MoneyRequest;
import com.bms.dto.TransactionResponse;
import com.bms.entity.Account;
import com.bms.entity.BankTransaction;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountOperationsServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private CurrentCustomerService currentCustomerService;
    @Mock
    private User customer;

    private AccountOperationsService operationsService;

    @BeforeEach
    void setUp() {
        operationsService = new AccountOperationsService(
                accountRepository, transactionRepository, currentCustomerService);
        when(customer.getId()).thenReturn(7L);
        when(currentCustomerService.requireCustomer("alice")).thenReturn(customer);
    }

    @Test
    void withdrawalRejectsInsufficientFundsWithoutChangingBalanceOrWritingLedger() {
        Account account = activeAccountWithBalance("10.00");
        when(accountRepository.findOwnedForUpdate(11L, 7L))
                .thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class, () -> operationsService.withdraw(
                "alice", 11L, new MoneyRequest(new BigDecimal("10.01"), null)));

        assertEquals(new BigDecimal("10.00"), account.getBalance());
        verify(accountRepository).findOwnedForUpdate(11L, 7L);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).saveAndFlush(any(BankTransaction.class));
    }

    @Test
    void successfulWithdrawalReturnsServerCalculatedBalanceAndRecordsTransaction() {
        Account account = activeAccountWithBalance("10.00");
        when(accountRepository.findOwnedForUpdate(11L, 7L))
                .thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any(BankTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = operationsService.withdraw(
                "alice", 11L, new MoneyRequest(new BigDecimal("4.25"), "Cash"));

        assertEquals(new BigDecimal("5.75"), account.getBalance());
        assertEquals(new BigDecimal("5.75"), response.balanceAfter());
        assertEquals("WITHDRAWAL", response.type());
        verify(accountRepository).findOwnedForUpdate(11L, 7L);
        verify(accountRepository).save(account);
        verify(transactionRepository).saveAndFlush(any(BankTransaction.class));
    }

    private Account activeAccountWithBalance(String balance) {
        Account account = new Account(
                customer, "1000000000000011", Account.Type.SAVINGS);
        account.credit(new BigDecimal(balance));
        return account;
    }
}
