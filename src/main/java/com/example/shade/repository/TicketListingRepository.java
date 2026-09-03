package com.example.shade.repository;

import com.example.shade.model.TicketListing;
import com.example.shade.model.TicketListingSide;
import com.example.shade.model.TicketListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketListingRepository extends JpaRepository<TicketListing, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketListing t WHERE t.id = :id")
    Optional<TicketListing> findByIdWithLock(@Param("id") Long id);

    Page<TicketListing> findByStatusOrderByCreatedAtDesc(TicketListingStatus status, Pageable pageable);

    Page<TicketListing> findByStatusAndSideOrderByCreatedAtDesc(
            TicketListingStatus status, TicketListingSide side, Pageable pageable);

    List<TicketListing> findBySellerChatIdAndStatusOrderByCreatedAtDesc(Long sellerChatId, TicketListingStatus status);

    long countBySellerChatIdAndStatus(Long sellerChatId, TicketListingStatus status);
}
