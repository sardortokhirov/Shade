package com.example.shade.config;

import com.example.shade.model.BlockedUser;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.DailyUserStatsRepository;
import com.example.shade.repository.UserBalanceRepository;
import com.example.shade.service.BonusService;
import com.example.shade.service.DailyStatsService;
import com.example.shade.service.LotteryService;
import com.example.shade.service.SystemConfigurationService;
import com.example.shade.service.UserLimitIncreaseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CommandLineRunner to simulate top-up process for testing
 * Simulates a 1,000,000 UZS top-up for chatId 1755953324
 * Processes deposit, limit increase, tickets, and referral credits
 * WITHOUT platform transfer or payment verification
 */
@Component
@RequiredArgsConstructor
public class TopUpSimulationRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(TopUpSimulationRunner.class);
    
    private static final Long TARGET_CHAT_ID = 1755953324L;
    private static final Long TOP_UP_AMOUNT = 1_000_000L;
    
    private final BlockedUserRepository blockedUserRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final DailyUserStatsRepository dailyUserStatsRepository;
    private final DailyStatsService dailyStatsService;
    private final LotteryService lotteryService;
    private final BonusService bonusService;
    private final SystemConfigurationService configurationService;
    private final UserLimitIncreaseService userLimitIncreaseService;
    
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void run(String... args) {
        logger.info("========================================");
        logger.info("Top-Up Simulation Runner Started");
        logger.info("ChatId: {}", TARGET_CHAT_ID);
        logger.info("Amount: {} UZS", TOP_UP_AMOUNT);
        logger.info("Time: {}", LocalDateTime.now(GMT_PLUS_5).format(FORMATTER));
        logger.info("========================================");
        
        try {
            // Step 1: Get user information
            String phoneNumber = getPhoneNumber();
            
            // Step 2: Display initial state
            displayInitialState(phoneNumber);
            
            // Step 3: Process the top-up
            processTopUp();
            
            // Step 4: Display final state
            displayFinalState(phoneNumber);
            
            logger.info("========================================");
            logger.info("Top-Up Simulation Completed Successfully");
            logger.info("========================================");
        } catch (Exception e) {
            logger.error("Error during top-up simulation", e);
        }
    }
    
    private String getPhoneNumber() {
        BlockedUser blockedUser = blockedUserRepository.findByChatId(TARGET_CHAT_ID)
                .orElse(null);
        String phoneNumber = (blockedUser != null && blockedUser.getPhoneNumber() != null) 
                ? blockedUser.getPhoneNumber() 
                : "N/A";
        logger.info("User Phone Number: {}", phoneNumber);
        return phoneNumber;
    }
    
    private void displayInitialState(String phoneNumber) {
        logger.info("----------------------------------------");
        logger.info("INITIAL STATE");
        logger.info("----------------------------------------");
        
        // Get user balance
        UserBalance balance = userBalanceRepository.findById(TARGET_CHAT_ID)
                .orElse(UserBalance.builder()
                        .chatId(TARGET_CHAT_ID)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        
        logger.info("Balance: {} UZS", balance.getBalance().longValue());
        logger.info("Tickets: {}", balance.getTickets());
        
        // Get limit information
        Long effectiveLimit = dailyStatsService.getEffectiveDailyLimit(TARGET_CHAT_ID);
        Long availableLimit = dailyStatsService.getAvailableLimit(TARGET_CHAT_ID);
        Long permanentLimitIncrease = userLimitIncreaseService.getPermanentLimitIncrease(TARGET_CHAT_ID);
        
        logger.info("Effective Daily Limit: {} UZS", effectiveLimit);
        logger.info("Available Limit: {} UZS", availableLimit);
        logger.info("Permanent Limit Increase: {} UZS", permanentLimitIncrease);
        
        // Get daily stats (read-only to avoid creating if doesn't exist)
        java.time.LocalDate today = java.time.LocalDate.now(GMT_PLUS_5);
        Long dailyTopUpAmount = dailyUserStatsRepository.findByChatIdAndDate(TARGET_CHAT_ID, today)
                .map(com.example.shade.model.DailyUserStats::getDailyTopUpAmount)
                .orElse(0L);
        logger.info("Daily Top-Up Amount: {} UZS", dailyTopUpAmount);
        
        // Get configuration
        BigDecimal topUpPercentage = configurationService.getTopUpDailyLimitIncreasePercentage();
        Long ticketCalculationAmount = configurationService.getTicketCalculationAmount();
        BigDecimal referralPercentage = configurationService.getReferralCommissionPercentage();
        
        logger.info("Top-Up Limit Increase Percentage: {}%", topUpPercentage);
        logger.info("Ticket Calculation Amount: {} UZS per ticket", ticketCalculationAmount);
        logger.info("Referral Commission Percentage: {}%", referralPercentage);
        
        logger.info("----------------------------------------");
    }
    
    private void processTopUp() {
        logger.info("----------------------------------------");
        logger.info("PROCESSING TOP-UP");
        logger.info("----------------------------------------");
        
        // Calculate expected values
        BigDecimal topUpPercentage = configurationService.getTopUpDailyLimitIncreasePercentage();
        long expectedLimitIncrease = BigDecimal.valueOf(TOP_UP_AMOUNT)
                .multiply(topUpPercentage)
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        
        Long ticketCalculationAmount = configurationService.getTicketCalculationAmount();
        long expectedTickets = TOP_UP_AMOUNT / ticketCalculationAmount;
        
        logger.info("Expected Limit Increase: {} UZS ({}% of {})", 
                expectedLimitIncrease, topUpPercentage, TOP_UP_AMOUNT);
        logger.info("Expected Tickets: {} ({} / {})", 
                expectedTickets, TOP_UP_AMOUNT, ticketCalculationAmount);
        
        // Step 1: Add top-up amount (this also increases permanent limit)
        logger.info("Step 1: Adding top-up amount to daily stats...");
        dailyStatsService.addTopUpAmount(TARGET_CHAT_ID, TOP_UP_AMOUNT);
        logger.info("✓ Added {} UZS to daily top-up amount", TOP_UP_AMOUNT);
        logger.info("✓ Permanent limit increased by {} UZS", expectedLimitIncrease);
        
        // Step 2: Award tickets
        if (expectedTickets > 0) {
            logger.info("Step 2: Awarding lottery tickets...");
            lotteryService.awardTickets(TARGET_CHAT_ID, expectedTickets);
            logger.info("✓ Awarded {} tickets", expectedTickets);
        } else {
            logger.info("Step 2: No tickets to award (amount {} < ticket calculation amount {})", 
                    TOP_UP_AMOUNT, ticketCalculationAmount);
        }
        
        // Step 3: Credit referral
        logger.info("Step 3: Processing referral credits...");
        bonusService.creditReferral(TARGET_CHAT_ID, TOP_UP_AMOUNT);
        logger.info("✓ Referral credits processed");
        
        logger.info("----------------------------------------");
    }
    
    private void displayFinalState(String phoneNumber) {
        logger.info("----------------------------------------");
        logger.info("FINAL STATE");
        logger.info("----------------------------------------");
        
        // Get user balance
        UserBalance balance = userBalanceRepository.findById(TARGET_CHAT_ID)
                .orElse(UserBalance.builder()
                        .chatId(TARGET_CHAT_ID)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        
        logger.info("Balance: {} UZS", balance.getBalance().longValue());
        logger.info("Tickets: {}", balance.getTickets());
        
        // Get limit information
        Long effectiveLimit = dailyStatsService.getEffectiveDailyLimit(TARGET_CHAT_ID);
        Long availableLimit = dailyStatsService.getAvailableLimit(TARGET_CHAT_ID);
        Long permanentLimitIncrease = userLimitIncreaseService.getPermanentLimitIncrease(TARGET_CHAT_ID);
        
        logger.info("Effective Daily Limit: {} UZS", effectiveLimit);
        logger.info("Available Limit: {} UZS", availableLimit);
        logger.info("Permanent Limit Increase: {} UZS", permanentLimitIncrease);
        
        // Calculate changes
        Long ticketCalculationAmount = configurationService.getTicketCalculationAmount();
        long ticketsAwarded = TOP_UP_AMOUNT / ticketCalculationAmount;
        
        BigDecimal topUpPercentage = configurationService.getTopUpDailyLimitIncreasePercentage();
        long limitIncrease = BigDecimal.valueOf(TOP_UP_AMOUNT)
                .multiply(topUpPercentage)
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        
        logger.info("----------------------------------------");
        logger.info("SUMMARY OF CHANGES");
        logger.info("----------------------------------------");
        logger.info("Top-Up Amount Processed: {} UZS", TOP_UP_AMOUNT);
        logger.info("Tickets Awarded: {}", ticketsAwarded);
        logger.info("Permanent Limit Increased: {} UZS", limitIncrease);
        logger.info("Referral Credits: Processed (if referrer exists)");
        logger.info("----------------------------------------");
    }
}
