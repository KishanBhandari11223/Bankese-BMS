package com.bms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bms.dto.FriendResponse;
import com.bms.dto.SaveFriendRequest;
import com.bms.entity.Account;
import com.bms.entity.Friend;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.FriendRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CurrentCustomerService currentCustomerService;
    @Mock
    private User customer;
    @Mock
    private User friendOwner;
    @Mock
    private Account friendAccount;

    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(
                friendRepository, accountRepository, currentCustomerService);
        when(customer.getId()).thenReturn(7L);
        when(currentCustomerService.requireCustomer("alice")).thenReturn(customer);
    }

    @Test
    void saveReturnsNameFromAccountOwner() {
        when(friendRepository.existsByOwner_IdAndRecipientAccountNumber(
                7L, "1000000000000002")).thenReturn(false);
        when(accountRepository.findByAccountNumber("1000000000000002"))
                .thenReturn(Optional.of(friendAccount));
        when(friendAccount.getOwner()).thenReturn(friendOwner);
        when(friendOwner.getId()).thenReturn(8L);
        when(friendOwner.getFullName()).thenReturn("Friend Name");
        when(friendAccount.getAccountNumber()).thenReturn("1000000000000002");
        when(friendAccount.getStatus()).thenReturn(Account.Status.ACTIVE);
        when(friendRepository.saveAndFlush(any(Friend.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FriendResponse response = friendService.save(
                "alice", new SaveFriendRequest("1000000000000002"));

        assertEquals("Friend Name", response.name());
        assertEquals("ACTIVE", response.accountStatus());
        verify(friendRepository).saveAndFlush(any(Friend.class));
    }

    @Test
    void listIsScopedToCurrentOwner() {
        when(friendRepository.findAllByOwner_IdOrderByIdDesc(7L))
                .thenReturn(List.of());

        assertEquals(0, friendService.list("alice").size());

        verify(friendRepository).findAllByOwner_IdOrderByIdDesc(7L);
    }

    @Test
    void removeCannotDeleteAnotherOwnersFriend() {
        when(friendRepository.findByIdAndOwner_Id(22L, 7L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> friendService.remove("alice", 22L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(friendRepository, never()).delete(any(Friend.class));
    }

    @Test
    void saveRejectsOwnAccount() {
        when(friendRepository.existsByOwner_IdAndRecipientAccountNumber(
                7L, "1000000000000001")).thenReturn(false);
        when(accountRepository.findByAccountNumber("1000000000000001"))
                .thenReturn(Optional.of(friendAccount));
        when(friendAccount.getOwner()).thenReturn(customer);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> friendService.save(
                        "alice", new SaveFriendRequest("1000000000000001")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(friendRepository, never()).saveAndFlush(any(Friend.class));
    }
}
