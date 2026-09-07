package com.example.shade.repository;

import com.example.shade.model.BlockedPhoneNumber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedPhoneNumberRepository extends JpaRepository<BlockedPhoneNumber, String> {

    void deleteByLinkedChatId(Long linkedChatId);
}
