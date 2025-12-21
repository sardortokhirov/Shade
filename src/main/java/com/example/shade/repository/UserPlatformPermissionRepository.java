package com.example.shade.repository;

import com.example.shade.model.UserPlatformPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPlatformPermissionRepository extends JpaRepository<UserPlatformPermission, Long> {
    Optional<UserPlatformPermission> findByUserId(String userId);

    @org.springframework.data.jpa.repository.Modifying
    @jakarta.transaction.Transactional
    void deleteByUserId(String userId);
}
