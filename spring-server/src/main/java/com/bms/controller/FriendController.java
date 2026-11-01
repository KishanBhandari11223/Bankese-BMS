package com.bms.controller;

import com.bms.dto.FriendResponse;
import com.bms.dto.SaveFriendRequest;
import com.bms.service.FriendService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public List<FriendResponse> list(@AuthenticationPrincipal String username) {
        return friendService.list(username);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FriendResponse save(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody SaveFriendRequest request) {
        return friendService.save(username, request);
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal String username,
            @PathVariable Long friendId) {
        friendService.remove(username, friendId);
    }
}
