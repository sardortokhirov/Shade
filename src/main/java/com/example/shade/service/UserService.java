package com.example.shade.service;

import com.example.shade.dto.*;
import com.example.shade.model.*;
import com.example.shade.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

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
    private final SystemConfigurationService configurationService;
    private final FeatureService featureService;

    @Transactional(readOnly = true)
    public Page<UserDTO> getUsers(Pageable pageable, UserFilter filter) {
        List<Long> candidateChatIds;
        long totalElements;
        boolean needsInMemoryPagination = false;
        
        // Step 1: Get candidate chatIds based on search filters
        if (filter != null && (filter.getSearchChatId() != null || filter.getSearchPhone() != null)) {
            List<Long> chatIdMatches = new ArrayList<>();
            List<Long> phoneMatches = new ArrayList<>();
            
            // Search by chatId if provided
            if (filter.getSearchChatId() != null) {
                String searchPattern = "%" + String.valueOf(filter.getSearchChatId()) + "%";
                
                // Search in User table
                List<Long> userChatIds = userRepository.findChatIdsBySearchPattern(searchPattern);
                
                // Search in BlockedUser table (to include blocked users without User records)
                List<Long> blockedChatIds = blockedUserRepository.findChatIdsBySearchPattern(searchPattern);
                
                // Combine and remove duplicates
                chatIdMatches.addAll(userChatIds);
                chatIdMatches.addAll(blockedChatIds);
                chatIdMatches = chatIdMatches.stream().distinct().sorted().toList();
            }
            
            // Search by phone number if provided
            if (filter.getSearchPhone() != null) {
                String phoneSearchPattern = "%" + filter.getSearchPhone() + "%";
                phoneMatches = blockedUserRepository.findChatIdsByPhonePattern(phoneSearchPattern);
            }
            
            // Combine results: if both filters provided, find intersection; otherwise use union
            if (filter.getSearchChatId() != null && filter.getSearchPhone() != null) {
                // Both filters: find intersection (users matching BOTH criteria)
                candidateChatIds = chatIdMatches.stream()
                        .filter(phoneMatches::contains)
                        .distinct()
                        .sorted()
                        .toList();
            } else if (filter.getSearchChatId() != null) {
                // Only chatId search
                candidateChatIds = chatIdMatches;
            } else {
                // Only phone search
                candidateChatIds = phoneMatches.stream().distinct().sorted().toList();
            }
            
            totalElements = candidateChatIds.size();
            needsInMemoryPagination = true;
        } else {
            // No search filters - use database pagination for efficiency
            Page<User> usersPage = userRepository.findAll(
                PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.ASC, "chatId")
                )
            );
            
            candidateChatIds = usersPage.getContent().stream()
                    .map(User::getChatId)
                    .toList();
            totalElements = usersPage.getTotalElements();
        }
        
        // Step 3: Apply filters and build UserDTOs (only for candidate chatIds)
        List<UserDTO> userDTOs = new ArrayList<>();
        
        for (Long chatId : candidateChatIds) {
            try {
                // Get User record (may not exist for blocked-only users)
                Optional<User> userOpt = userRepository.findByChatId(chatId);
                
                // Get blocked status
                Optional<BlockedUser> blockedUser = blockedUserRepository.findByChatId(chatId);
                boolean isBlocked = blockedUser.isPresent() && "BLOCKED".equals(blockedUser.get().getPhoneNumber());
                String phoneNumber = blockedUser.map(BlockedUser::getPhoneNumber)
                        .orElse(null);
                
                // Apply blocked filter
                if (filter != null && filter.getBlocked() != null && filter.getBlocked() != isBlocked) {
                    continue;
                }
                
                // Apply language filter (only if User record exists)
                if (filter != null && filter.getLanguage() != null) {
                    if (userOpt.isEmpty()) {
                        // Blocked user without User record - skip if language filter is set
                        continue;
                    }
                    if (!filter.getLanguage().equals(userOpt.get().getLanguage().toString())) {
                        continue;
                    }
                }
                
                // Phone search filter is already applied in Step 1 (database query)
                // No need to filter again here since candidateChatIds already contains matching users
                
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
                BigDecimal permanentLimitIncreaseBD = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
                Long permanentLimitIncrease = permanentLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimitReadOnly(chatId);
                Long availableLimit = dailyStatsService.getAvailableLimitReadOnly(chatId);
                
                // Determine language - use from User if exists, otherwise default to UZ
                String language = userOpt.map(u -> u.getLanguage().toString())
                        .orElse("UZ");
                
                UserDTO userDTO = new UserDTO(
                        chatId,
                        language,
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
            } catch (Exception e) {
                // Log error but continue processing other users
                logger.warn("Error processing user with chatId {}: {}", chatId, e.getMessage());
                continue;
            }
        }
        
        // Step 4: Apply pagination if search filters were provided (results already paginated if not)
        if (needsInMemoryPagination) {
            int page = pageable.getPageNumber();
            int size = pageable.getPageSize();
            int start = page * size;
            int end = Math.min(start + size, userDTOs.size());
            
            List<UserDTO> paginatedDTOs = (start < userDTOs.size()) 
                    ? userDTOs.subList(start, end) 
                    : new ArrayList<>();
            
            return new PageImpl<>(paginatedDTOs, pageable, userDTOs.size());
        } else {
            // No search filters - return paginated results (already paginated from database)
            return new PageImpl<>(userDTOs, pageable, totalElements);
        }
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
        
        // Get limit information (using read-only methods to avoid creating stats in read-only transaction)
        // Fetch UserLimitIncrease entity directly to get all database information
        Optional<com.example.shade.model.UserLimitIncrease> userLimitIncreaseOpt = 
                userLimitIncreaseRepository.findByChatId(chatId);
        BigDecimal permanentLimitIncreaseBD = userLimitIncreaseOpt
                .map(com.example.shade.model.UserLimitIncrease::getAccumulatedLimitIncrease)
                .orElse(BigDecimal.ZERO);
        Long permanentLimitIncrease = permanentLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        // Format with 8 decimal places
        String permanentLimitIncreaseFormatted = permanentLimitIncreaseBD.setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
        LocalDateTime permanentLimitLastUpdated = userLimitIncreaseOpt
                .map(com.example.shade.model.UserLimitIncrease::getLastUpdated)
                .orElse(null);
        
        Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimitReadOnly(chatId);
        Long availableLimit = dailyStatsService.getAvailableLimitReadOnly(chatId);
        
        // Get base daily limit for detailed breakdown
        Long baseDailyLimit = configurationService.getDailyBonusTransferLimit();
        
        // Get daily stats (read-only - don't create if doesn't exist)
        LocalDate today = LocalDate.now(java.time.ZoneId.of("GMT+5"));
        Optional<DailyUserStats> dailyStatsOpt = dailyUserStatsRepository.findByChatIdAndDate(chatId, today);
        Long dailyTopUpAmount = dailyStatsOpt.map(DailyUserStats::getDailyTopUpAmount).orElse(0L);
        Long dailyTransferAmount = dailyStatsOpt.map(DailyUserStats::getDailyTransferAmount).orElse(0L);
        Long dailyLimitIncrease = dailyStatsOpt.map(DailyUserStats::getDailyLimitIncrease).orElse(0L);
        LocalDate dailyStatsDate = dailyStatsOpt.map(DailyUserStats::getDate).orElse(null);
        LocalDateTime dailyStatsLastUpdated = dailyStatsOpt.map(DailyUserStats::getLastUpdated).orElse(null);
        LocalDateTime lastUpdated = dailyStatsLastUpdated; // Keep for backward compatibility
        
        // Build detailed limit breakdown string
        String limitBreakdown = buildLimitBreakdown(baseDailyLimit, permanentLimitIncreaseBD, permanentLimitIncrease, 
                dailyLimitIncrease, effectiveDailyLimit, dailyTopUpAmount, dailyTransferAmount, availableLimit);
        
        return new UserDetailDTO(
                chatId,
                user.getLanguage().toString(),
                phoneNumber,
                isBlocked,
                balance,
                tickets,
                registeredAt,
                permanentLimitIncrease,
                permanentLimitIncreaseBD,
                permanentLimitIncreaseFormatted,
                permanentLimitLastUpdated,
                effectiveDailyLimit,
                availableLimit,
                platformsUsed,
                dailyTopUpAmount,
                dailyTransferAmount,
                dailyLimitIncrease,
                dailyStatsDate,
                dailyStatsLastUpdated,
                lastLotteryPlayTime,
                lastUpdated,
                baseDailyLimit,
                limitBreakdown
        );
    }
    
    /**
     * Builds a detailed limit breakdown string for admin display
     */
    private String buildLimitBreakdown(Long baseDailyLimit, BigDecimal permanentLimitIncreaseBD, 
            Long permanentLimitIncrease, Long dailyLimitIncrease, Long effectiveDailyLimit,
            Long dailyTopUpAmount, Long dailyTransferAmount, Long availableLimit) {
        StringBuilder breakdown = new StringBuilder();
        breakdown.append("Base Daily Limit: ").append(String.format("%,d", baseDailyLimit)).append(" UZS\n");
        breakdown.append("Permanent Increase (rounded): ").append(String.format("%,d", permanentLimitIncrease)).append(" UZS\n");
        breakdown.append("Permanent Increase (precise, 8 decimals): ").append(permanentLimitIncreaseBD.setScale(8, java.math.RoundingMode.HALF_UP).toPlainString()).append(" UZS\n");
        breakdown.append("Daily Limit Increase (from lottery): ").append(String.format("%,d", dailyLimitIncrease)).append(" UZS\n");
        breakdown.append("Effective Daily Limit: ").append(String.format("%,d", effectiveDailyLimit))
                .append(" UZS (= ").append(baseDailyLimit).append(" + ").append(permanentLimitIncrease)
                .append(" + ").append(dailyLimitIncrease).append(")\n");
        breakdown.append("\nToday's Activity:\n");
        breakdown.append("- Daily Top-Ups: ").append(String.format("%,d", dailyTopUpAmount)).append(" UZS\n");
        breakdown.append("- Daily Transfers: ").append(String.format("%,d", dailyTransferAmount)).append(" UZS\n");
        breakdown.append("\nAvailable Limit: ").append(String.format("%,d", availableLimit)).append(" UZS");
        
        boolean payToggleEnabled = featureService.isPayToggleEnabled();
        if (payToggleEnabled) {
            breakdown.append("\n(Pay Toggle: ON - ignores deposits)");
        } else {
            breakdown.append("\n(Pay Toggle: OFF - min(effective limit, deposits) - transfers)");
        }
        
        return breakdown.toString();
    }

    @Transactional(readOnly = true)
    public Page<HizmatRequest> getUserTransfers(Long chatId, Pageable pageable, TransferFilter filter) {
        RequestStatus status = filter != null ? filter.getStatus() : null;
        String platform = filter != null ? filter.getPlatform() : null;
        RequestType type = filter != null ? filter.getType() : null;
        LocalDateTime startDate = filter != null ? filter.getStartDate() : null;
        LocalDateTime endDate = filter != null ? filter.getEndDate() : null;
        
        // Create pageable with default sort
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        // Build specification dynamically
        Specification<HizmatRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always filter by chatId
            predicates.add(cb.equal(root.get("chatId"), chatId));
            
            // Add optional filters
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (platform != null && !platform.isEmpty()) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        return hizmatRequestRepository.findAll(spec, sortedPageable);
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
        java.math.BigDecimal oldLimitBD = limitIncrease.getAccumulatedLimitIncrease();
        Long oldLimit = oldLimitBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        java.math.BigDecimal newLimitBD = java.math.BigDecimal.valueOf(permanentLimitIncrease);
        
        // Validation: Prevent accidental reset to 0 when current limit > 0
        if (permanentLimitIncrease == 0 && oldLimit > 0) {
            logger.error("ATTEMPT TO RESET PERMANENT LIMIT TO ZERO - chatId: {}, current limit: {}. " +
                    "This operation is blocked to prevent accidental data loss. " +
                    "If this is intentional, use the resetLimit() method with explicit confirmation.", 
                    chatId, oldLimit);
            throw new IllegalArgumentException(
                    String.format("Cannot reset permanent limit increase to 0 when current limit is %d. " +
                            "Permanent limits should never be reset automatically. " +
                            "If this is intentional, contact system administrator.", oldLimit));
        }
        
        // Warning: Log if limit is being decreased (but allow it if not going to 0)
        if (permanentLimitIncrease < oldLimit && permanentLimitIncrease > 0) {
            logger.warn("PERMANENT LIMIT DECREASE - chatId: {}, decreasing from {} to {}. " +
                    "This is unusual and may indicate an error.", 
                    chatId, oldLimit, permanentLimitIncrease);
        }
        
        // Detailed audit logging
        String changeType;
        if (permanentLimitIncrease > oldLimit) {
            changeType = "INCREASE";
        } else if (permanentLimitIncrease < oldLimit) {
            changeType = "DECREASE";
        } else {
            changeType = "NO_CHANGE";
        }
        
        logger.info("PERMANENT LIMIT UPDATE [{}] - chatId: {}, old: {}, new: {}, difference: {}", 
                changeType, chatId, oldLimit, permanentLimitIncrease, 
                permanentLimitIncrease - oldLimit);
        
        limitIncrease.setAccumulatedLimitIncrease(newLimitBD);
        limitIncrease.setLastUpdated(LocalDateTime.now());
        userLimitIncreaseRepository.save(limitIncrease);
        
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
    public Page<DailyUserStatsDTO> getUserDailyStats(Long chatId, LocalDate date, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // Validate user exists
        if (!userRepository.existsById(chatId)) {
            throw new RuntimeException("User not found with chatId: " + chatId);
        }
        
        Page<DailyUserStats> statsPage;
        
        // If single date is provided, it takes precedence
        if (date != null) {
            Optional<DailyUserStats> stats = dailyUserStatsRepository.findByChatIdAndDate(chatId, date);
            if (stats.isPresent()) {
                List<DailyUserStatsDTO> dtoList = List.of(mapToDTO(stats.get()));
                Page<DailyUserStatsDTO> resultPage = new PageImpl<>(dtoList, PageRequest.of(0, 1), 1);
                return resultPage;
            } else {
                return Page.empty(pageable);
            }
        } else if (startDate != null && endDate != null) {
            // Date range query
            statsPage = dailyUserStatsRepository.findByChatIdAndDateBetween(chatId, startDate, endDate, pageable);
        } else {
            // No date filter - get all stats ordered by date descending
            statsPage = dailyUserStatsRepository.findByChatIdOrderByDateDesc(chatId, pageable);
        }
        
        // Map to DTOs
        List<DailyUserStatsDTO> dtoList = statsPage.getContent().stream()
                .map(this::mapToDTO)
                .toList();
        
        return new PageImpl<>(dtoList, statsPage.getPageable(), statsPage.getTotalElements());
    }
    
    private DailyUserStatsDTO mapToDTO(DailyUserStats stats) {
        return new DailyUserStatsDTO(
                stats.getDate(),
                stats.getDailyTopUpAmount(),
                stats.getDailyTransferAmount(),
                stats.getDailyLimitIncrease(),
                stats.getLastUpdated()
        );
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
