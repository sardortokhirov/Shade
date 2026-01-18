package com.example.shade.service;

import com.example.shade.dto.*;
import com.example.shade.model.*;
import com.example.shade.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final UserRepository userRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final HizmatRequestRepository hizmatRequestRepository;
    private final DailyStatsService dailyStatsService;
    private final UserLimitIncreaseService userLimitIncreaseService;
    private final DailyUserStatsRepository dailyUserStatsRepository;
    private final UserLimitIncreaseRepository userLimitIncreaseRepository;

    @Transactional(readOnly = true)
    public Page<UserDTO> getUsers(Pageable pageable, UserFilter filter) {
        // Get all users with pagination
        Page<User> usersPage = userRepository.findAll(
            org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "chatId")
            )
        );

        List<UserDTO> userDTOs = new ArrayList<>();
        
        for (User user : usersPage.getContent()) {
            Long chatId = user.getChatId();
            
            // Apply filters
            if (filter != null) {
                if (filter.getSearchChatId() != null && !filter.getSearchChatId().equals(chatId)) {
                    continue;
                }
                
                if (filter.getLanguage() != null && !filter.getLanguage().equals(user.getLanguage().toString())) {
                    continue;
                }
            }
            
            // Get blocked status
            Optional<BlockedUser> blockedUser = blockedUserRepository.findByChatId(chatId);
            boolean isBlocked = blockedUser.isPresent() && "BLOCKED".equals(blockedUser.get().getPhoneNumber());
            String phoneNumber = blockedUser.map(BlockedUser::getPhoneNumber)
                    .orElse(null);
            
            // Apply blocked filter
            if (filter != null && filter.getBlocked() != null && filter.getBlocked() != isBlocked) {
                continue;
            }
            
            // Apply phone search filter
            if (filter != null && filter.getSearchPhone() != null) {
                if (phoneNumber == null || !phoneNumber.contains(filter.getSearchPhone())) {
                    continue;
                }
            }
            
            // Get balance
            Optional<UserBalance> userBalance = userBalanceRepository.findById(chatId);
            BigDecimal balance = userBalance.map(UserBalance::getBalance).orElse(BigDecimal.ZERO);
            Long tickets = userBalance.map(UserBalance::getTickets).orElse(0L);
            
            // Apply hasBalance filter
            if (filter != null && filter.getHasBalance() != null) {
                boolean hasBalance = balance.compareTo(BigDecimal.ZERO) > 0;
                if (filter.getHasBalance() != hasBalance) {
                    continue;
                }
            }
            
            // Get registration date (earliest request)
            LocalDateTime registeredAt = hizmatRequestRepository.findEarliestByChatId(chatId)
                    .orElse(null);
            
            // Get platforms used
            List<String> platformsUsed = hizmatRequestRepository.findDistinctPlatformsByChatId(chatId);
            
            // Get limit information (using read-only methods to avoid creating stats in read-only transaction)
            Long permanentLimitIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
            Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimitReadOnly(chatId);
            Long availableLimit = dailyStatsService.getAvailableLimitReadOnly(chatId);
            
            UserDTO userDTO = new UserDTO(
                    chatId,
                    user.getLanguage().toString(),
                    phoneNumber,
                    isBlocked,
                    balance,
                    tickets,
                    registeredAt,
                    permanentLimitIncrease,
                    effectiveDailyLimit,
                    availableLimit,
                    platformsUsed
            );
            
            userDTOs.add(userDTO);
        }
        
        return new PageImpl<>(userDTOs, pageable, usersPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserDetailDTO getUserDetails(Long chatId) {
        // Get user
        User user = userRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("User not found with chatId: " + chatId));
        
        // Get blocked status
        Optional<BlockedUser> blockedUser = blockedUserRepository.findByChatId(chatId);
        boolean isBlocked = blockedUser.isPresent() && "BLOCKED".equals(blockedUser.get().getPhoneNumber());
        String phoneNumber = blockedUser.map(BlockedUser::getPhoneNumber)
                .orElse(null);
        
        // Get balance
        Optional<UserBalance> userBalance = userBalanceRepository.findById(chatId);
        BigDecimal balance = userBalance.map(UserBalance::getBalance).orElse(BigDecimal.ZERO);
        Long tickets = userBalance.map(UserBalance::getTickets).orElse(0L);
        LocalDateTime lastLotteryPlayTime = userBalance.map(UserBalance::getLastLotteryPlayTime)
                .orElse(null);
        
        // Get registration date
        LocalDateTime registeredAt = hizmatRequestRepository.findEarliestByChatId(chatId)
                .orElse(null);
        
        // Get platforms used
        List<String> platformsUsed = hizmatRequestRepository.findDistinctPlatformsByChatId(chatId);
        
        // Get limit information
        Long permanentLimitIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimit(chatId);
        Long availableLimit = dailyStatsService.getAvailableLimit(chatId);
        
        // Get daily stats
        DailyUserStats dailyStats = dailyStatsService.getOrCreateTodayStats(chatId);
        Long dailyTopUpAmount = dailyStats.getDailyTopUpAmount();
        Long dailyTransferAmount = dailyStats.getDailyTransferAmount();
        Long dailyLimitIncrease = dailyStats.getDailyLimitIncrease();
        LocalDateTime lastUpdated = dailyStats.getLastUpdated();
        
        return new UserDetailDTO(
                chatId,
                user.getLanguage().toString(),
                phoneNumber,
                isBlocked,
                balance,
                tickets,
                registeredAt,
                permanentLimitIncrease,
                effectiveDailyLimit,
                availableLimit,
                platformsUsed,
                dailyTopUpAmount,
                dailyTransferAmount,
                dailyLimitIncrease,
                lastLotteryPlayTime,
                lastUpdated
        );
    }

    @Transactional(readOnly = true)
    public Page<HizmatRequest> getUserTransfers(Long chatId, Pageable pageable, TransferFilter filter) {
        RequestStatus status = filter != null ? filter.getStatus() : null;
        String platform = filter != null ? filter.getPlatform() : null;
        RequestType type = filter != null ? filter.getType() : null;
        LocalDateTime startDate = filter != null ? filter.getStartDate() : null;
        LocalDateTime endDate = filter != null ? filter.getEndDate() : null;
        
        // Create pageable with default sort
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        return hizmatRequestRepository.findByChatIdAndFilters(
                chatId,
                status,
                platform,
                type,
                startDate,
                endDate,
                sortedPageable
        );
    }

    @Transactional
    public UserBalance updateBalance(Long chatId, BigDecimal balance) {
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance must be non-negative");
        }
        
        UserBalance userBalance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        
        BigDecimal oldBalance = userBalance.getBalance();
        userBalance.setBalance(balance);
        userBalanceRepository.save(userBalance);
        
        logger.info("Updated balance for chatId {}: {} -> {}", chatId, oldBalance, balance);
        return userBalance;
    }

    @Transactional
    public UserBalance updateTickets(Long chatId, Long tickets) {
        if (tickets == null || tickets < 0) {
            throw new IllegalArgumentException("Tickets must be non-negative");
        }
        
        UserBalance userBalance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        
        Long oldTickets = userBalance.getTickets();
        userBalance.setTickets(tickets);
        userBalanceRepository.save(userBalance);
        
        logger.info("Updated tickets for chatId {}: {} -> {}", chatId, oldTickets, tickets);
        return userBalance;
    }

    @Transactional
    public UserLimitIncrease updateLimit(Long chatId, Long permanentLimitIncrease) {
        if (permanentLimitIncrease == null || permanentLimitIncrease < 0) {
            throw new IllegalArgumentException("Permanent limit increase must be non-negative");
        }
        
        UserLimitIncrease limitIncrease = userLimitIncreaseService.getOrCreate(chatId);
        Long oldLimit = limitIncrease.getAccumulatedLimitIncrease();
        limitIncrease.setAccumulatedLimitIncrease(permanentLimitIncrease);
        limitIncrease.setLastUpdated(LocalDateTime.now());
        userLimitIncreaseRepository.save(limitIncrease);
        
        logger.info("Updated permanent limit increase for chatId {}: {} -> {}", chatId, oldLimit, permanentLimitIncrease);
        return limitIncrease;
    }

    @Transactional
    public void blockUser(Long chatId) {
        Optional<BlockedUser> existing = blockedUserRepository.findByChatId(chatId);
        if (existing.isPresent() && "BLOCKED".equals(existing.get().getPhoneNumber())) {
            throw new IllegalStateException("User is already blocked");
        }
        
        BlockedUser blockedUser = existing.orElse(BlockedUser.builder().chatId(chatId).build());
        blockedUser.setPhoneNumber("BLOCKED");
        blockedUserRepository.save(blockedUser);
        
        logger.info("Blocked user with chatId: {}", chatId);
    }

    @Transactional
    public void unblockUser(Long chatId) {
        Optional<BlockedUser> blockedUser = blockedUserRepository.findByChatId(chatId);
        if (blockedUser.isEmpty() || !"BLOCKED".equals(blockedUser.get().getPhoneNumber())) {
            throw new IllegalStateException("User is not blocked");
        }
        
        blockedUserRepository.deleteById(chatId);
        logger.info("Unblocked user with chatId: {}", chatId);
    }

    @Transactional
    public void deleteUser(Long chatId, String deleteType) {
        if (!userRepository.existsById(chatId)) {
            throw new RuntimeException("User not found with chatId: " + chatId);
        }
        
        if ("hard".equalsIgnoreCase(deleteType)) {
            // Hard delete - remove all user data
            userBalanceRepository.deleteById(chatId);
            blockedUserRepository.deleteById(chatId);
            userLimitIncreaseRepository.deleteById(chatId);
            // Delete all daily stats for this user
            List<DailyUserStats> allStats = dailyUserStatsRepository.findAll();
            List<DailyUserStats> userStats = allStats.stream()
                    .filter(s -> s.getChatId().equals(chatId))
                    .toList();
            if (!userStats.isEmpty()) {
                dailyUserStatsRepository.deleteAll(userStats);
            }
            userRepository.deleteById(chatId);
            userRepository.deleteById(chatId);
            logger.warn("Hard deleted user with chatId: {}", chatId);
        } else {
            // Soft delete - reset balance, tickets, and block user
            updateBalance(chatId, BigDecimal.ZERO);
            updateTickets(chatId, 0L);
            try {
                blockUser(chatId);
            } catch (IllegalStateException e) {
                // User already blocked, continue
            }
            logger.info("Soft deleted user with chatId: {}", chatId);
        }
    }

    @Transactional
    public User updateLanguage(Long chatId, String language) {
        User user = userRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("User not found with chatId: " + chatId));
        
        try {
            Language lang = Language.valueOf(language.toUpperCase());
            user.setLanguage(lang);
            userRepository.save(user);
            logger.info("Updated language for chatId {}: {}", chatId, language);
            return user;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid language: " + language + ". Must be UZ or RU");
        }
    }

    @Transactional
    public void resetDailyStats(Long chatId) {
        LocalDate today = LocalDate.now();
        Optional<DailyUserStats> stats = dailyUserStatsRepository.findByChatIdAndDate(chatId, today);
        
        if (stats.isPresent()) {
            DailyUserStats dailyStats = stats.get();
            dailyStats.setDailyTopUpAmount(0L);
            dailyStats.setDailyTransferAmount(0L);
            dailyStats.setDailyLimitIncrease(0L);
            dailyStats.setLastUpdated(LocalDateTime.now());
            dailyUserStatsRepository.save(dailyStats);
            logger.info("Reset daily stats for chatId {} on date {}", chatId, today);
        }
    }

    @Transactional
    public void resetBalance(Long chatId) {
        updateBalance(chatId, BigDecimal.ZERO);
        updateTickets(chatId, 0L);
        logger.info("Reset balance and tickets for chatId: {}", chatId);
    }

    @Transactional(readOnly = true)
    public UserSummaryDTO getUserSummary(Long chatId) {
        if (!userRepository.existsById(chatId)) {
            throw new RuntimeException("User not found with chatId: " + chatId);
        }
        
        Long totalTopUps = hizmatRequestRepository.sumTopUpAmountByChatId(chatId);
        Long totalTransfers = hizmatRequestRepository.sumTransferAmountByChatId(chatId);
        Long totalRequests = hizmatRequestRepository.countByChatId(chatId);
        Long approvedRequests = hizmatRequestRepository.countByChatIdAndStatus(chatId, RequestStatus.APPROVED);
        Long canceledRequests = hizmatRequestRepository.countByChatIdAndStatus(chatId, RequestStatus.CANCELED);
        Long pendingRequests = hizmatRequestRepository.countByChatIdAndStatus(chatId, RequestStatus.PENDING);
        Long failedRequests = hizmatRequestRepository.countByChatIdAndStatus(chatId, RequestStatus.FAILED);
        
        LocalDateTime firstRequestDate = hizmatRequestRepository.findFirstRequestDateByChatId(chatId).orElse(null);
        LocalDateTime lastRequestDate = hizmatRequestRepository.findLastRequestDateByChatId(chatId).orElse(null);
        
        return new UserSummaryDTO(
                totalTopUps != null ? totalTopUps : 0L,
                totalTransfers != null ? totalTransfers : 0L,
                totalRequests != null ? totalRequests : 0L,
                approvedRequests != null ? approvedRequests : 0L,
                canceledRequests != null ? canceledRequests : 0L,
                pendingRequests != null ? pendingRequests : 0L,
                failedRequests != null ? failedRequests : 0L,
                firstRequestDate,
                lastRequestDate
        );
    }

    @Transactional
    public List<Long> bulkBlockUsers(List<Long> chatIds) {
        List<Long> blocked = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        
        for (Long chatId : chatIds) {
            try {
                blockUser(chatId);
                blocked.add(chatId);
            } catch (Exception e) {
                logger.warn("Failed to block user {}: {}", chatId, e.getMessage());
                failed.add(chatId);
            }
        }
        
        logger.info("Bulk block completed: {} succeeded, {} failed", blocked.size(), failed.size());
        return blocked;
    }

    @Transactional
    public List<Long> bulkUnblockUsers(List<Long> chatIds) {
        List<Long> unblocked = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        
        for (Long chatId : chatIds) {
            try {
                unblockUser(chatId);
                unblocked.add(chatId);
            } catch (Exception e) {
                logger.warn("Failed to unblock user {}: {}", chatId, e.getMessage());
                failed.add(chatId);
            }
        }
        
        logger.info("Bulk unblock completed: {} succeeded, {} failed", unblocked.size(), failed.size());
        return unblocked;
    }
}
