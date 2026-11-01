package com.bms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.dto.TransferRequest;
import com.bms.dto.TransferResponse;
import com.bms.entity.Account;
import com.bms.entity.BankTransaction;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private CurrentCustomerService currentCustomerService;
    @Mock
    private User customer;

    private TransferService transferService;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                accountRepository, transactionRepository, currentCustomerService);
        source = mock(Account.class);
        destination = mock(Account.class);
        when(customer.getId()).thenReturn(7L);
        when(currentCustomerService.requireCustomer("alice")).thenReturn(customer);
        when(source.getAccountNumber()).thenReturn("1000000000000001");
        when(source.getOwner()).thenReturn(customer);
        when(source.getStatus()).thenReturn(Account.Status.ACTIVE);
        when(source.getCurrency()).thenReturn("INR");
        when(destination.getAccountNumber()).thenReturn("1000000000000002");
        when(destination.getStatus()).thenReturn(Account.Status.ACTIVE);
        when(destination.getCurrency()).thenReturn("INR");
        when(accountRepository.findByIdAndOwner_Id(11L, 7L))
                .thenReturn(java.util.Optional.of(source));
        when(accountRepository.findTransferAccountsForUpdate(List.of(
                "1000000000000001", "1000000000000002")))
                .thenReturn(List.of(source, destination));
    }

    @Test
    void transferMovesBothBalancesAndWritesMatchingLedgerLegs() {
                when(source.getBalance()).thenReturn(new BigDecimal("100.00"));
                when(destination.getBalance()).thenReturn(new BigDecimal("25.00"));
        TransferResponse response = transferService.transfer(
                "alice", 11L,
                new TransferRequest("1000000000000002", new BigDecimal("12.50"), "Rent"));

        assertEquals(new BigDecimal("87.50"), response.sourceBalance());
        assertEquals(new BigDecimal("37.50"), response.destinationBalance());
        assertEquals("COMPLETED", response.status());
        verify(source).debit(new BigDecimal("12.50"));
        verify(destination).credit(new BigDecimal("12.50"));
        verify(accountRepository).saveAll(List.of(source, destination));

        ArgumentCaptor<Iterable<BankTransaction>> entries =
                ArgumentCaptor.forClass(Iterable.class);
        verify(transactionRepository).saveAll(entries.capture());
        List<BankTransaction> ledger = (List<BankTransaction>) entries.getValue();
        assertEquals(2, ledger.size());
        assertEquals(ledger.get(0).getReferenceNumber(), ledger.get(1).getReferenceNumber());
        assertEquals(BankTransaction.Type.TRANSFER_OUT, ledger.get(0).getType());
        assertEquals(BankTransaction.Type.TRANSFER_IN, ledger.get(1).getType());
    }

    @Test
    void transferRejectsInsufficientFundsWithoutWriting() {
        when(source.getBalance()).thenReturn(new BigDecimal("10.00"));

        assertThrows(ResponseStatusException.class, () -> transferService.transfer(
                "alice", 11L,
                new TransferRequest("1000000000000002", new BigDecimal("10.01"), null)));

        verify(accountRepository, never()).saveAll(anyList());
        verify(transactionRepository, never()).saveAll(anyList());
    }
}