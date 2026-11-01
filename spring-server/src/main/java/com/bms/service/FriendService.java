package com.bms.service;

import com.bms.dto.FriendResponse;
import com.bms.dto.SaveFriendRequest;
import com.bms.entity.Account;
import com.bms.entity.Friend;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.FriendRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final AccountRepository accountRepository;
    private final CurrentCustomerService currentCustomerService;

    public FriendService(
            FriendRepository friendRepository,
            AccountRepository accountRepository,
            CurrentCustomerService currentCustomerService) {
        this.friendRepository = friendRepository;
        this.accountRepository = accountRepository;
        this.currentCustomerService = currentCustomerService;
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> list(String username) {
        User customer = currentCustomerService.requireCustomer(username);
        return friendRepository.findAllByOwner_IdOrderByIdDesc(customer.getId())
                .stream().map(FriendResponse::from).toList();
    }

    @Transactional
    public FriendResponse save(String username, SaveFriendRequest request) {
        User customer = currentCustomerService.requireCustomer(username);
        String accountNumber = request.accountNumber().trim();
        if (friendRepository.existsByOwner_IdAndRecipientAccountNumber(
                customer.getId(), accountNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This friend is already saved");
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recipient account not found"));
        if (account.getOwner().getId().equals(customer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You cannot save your own account");
        }

        try {
            return FriendResponse.from(friendRepository.saveAndFlush(
                    new Friend(customer, account)));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recipient account changed or was already saved");
        }
    }

    @Transactional
    public void remove(String username, Long friendId) {
        User customer = currentCustomerService.requireCustomer(username);
        Friend friend = friendRepository.findByIdAndOwner_Id(friendId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Saved friend not found"));
        friendRepository.delete(friend);
    }
}
