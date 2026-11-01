package com.bms.repository;

import com.bms.entity.Friend;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    List<Friend> findAllByOwner_IdOrderByIdDesc(Long ownerId);

    Optional<Friend> findByIdAndOwner_Id(Long id, Long ownerId);

    boolean existsByOwner_IdAndRecipientAccountNumber(
            Long ownerId, String recipientAccountNumber);
}
