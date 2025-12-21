package com.example.shade.repository;

import com.example.shade.model.AllowedPromoUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllowedPromoUserRepository extends JpaRepository<AllowedPromoUser, Long> {
    boolean existsByUserId(String userId);

    Optional<AllowedPromoUser> findByUserId(String userId);

    void deleteByUserId(String userId);
}
