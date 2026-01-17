package com.example.shade.repository;

import com.example.shade.model.UserLimitIncrease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserLimitIncreaseRepository extends JpaRepository<UserLimitIncrease, Long> {
    Optional<UserLimitIncrease> findByChatId(Long chatId);
}
