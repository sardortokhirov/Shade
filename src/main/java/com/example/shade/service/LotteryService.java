package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.dto.OverallBalanceTicketsDTO;
import com.example.shade.model.*;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import com.example.shade.bot.AdminBotMessageSender;

import java.util.concurrent.CompletableFuture;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LotteryService {

    /** Result of an in-memory lottery draw: winnings per ticket and prize count decrements to persist. */
    private static record DrawResult(Map<Long, BigDecimal> winnings, Map<Long, Integer> prizeIdToDecrement) {}

    /** Mutable in-memory prize state for the draw loop (no JPA in hot path). */
    private static class PrizeState {
        final long id;
        final BigDecimal amount;
        int count;

        PrizeState(long id, BigDecimal amount, int count) {
            this.id = id;
            this.amount = amount;
            this.count = count;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(LotteryService.class);
    private final UserBalanceRepository userBalanceRepository;
    private final LotteryPrizeRepository lotteryPrizeRepository;
    private final HizmatRequestRepository hizmatRequestRepository;
    private final LottoBotService lottoBotService;
    private final BlockedUserRepository blockedUserRepository;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final LanguageSessionService languageSessionService;
    private final Random random = new Random();
    private final SystemConfigurationService configurationService;

    /**
     * Builds mutable in-memory prize state from entities (only valid prizes: amount > 0 and count > 0).
     */
    private static List<PrizeState> buildPrizeState(List<LotteryPrize> prizes) {
        List<PrizeState> state = new ArrayList<>();
        for (LotteryPrize p : prizes) {
            if (p.getAmount().compareTo(BigDecimal.ZERO) > 0 && p.getNumberOfPrize() != null && p.getNumberOfPrize() > 0) {
                state.add(new PrizeState(p.getId(), p.getAmount(), p.getNumberOfPrize()));
            }
        }
        return state;
    }

    /**
     * Performs all draws in memory: O(1) ticket selection (swap-with-last), weighted prize pick, no DB.
     * Returns winnings (ticket -> amount) and prizeIdToDecrement (prize id -> count to subtract).
     */
    private DrawResult drawInMemory(long numberOfPlays, List<PrizeState> prizes) {
        int n = (int) numberOfPlays;
        Map<Long, BigDecimal> winnings = new HashMap<>();
        Map<Long, Integer> prizeIdToDecrement = new HashMap<>();

        long[] tickets = new long[n];
        for (int i = 0; i < n; i++) {
            tickets[i] = i + 1;
        }
        int size = n;

        for (int i = 0; i < n; i++) {
            int totalWeight = 0;
            for (PrizeState p : prizes) {
                if (p.count > 0) totalWeight += p.count;
            }
            if (totalWeight == 0) {
                logger.warn("No prizes left during draw. Breaking after {} plays.", i);
                break;
            }

            int idx = random.nextInt(size);
            long selectedTicket = tickets[idx];
            tickets[idx] = tickets[size - 1];
            size--;

            int r = random.nextInt(totalWeight);
            int cum = 0;
            for (PrizeState p : prizes) {
                if (p.count <= 0) continue;
                cum += p.count;
                if (r < cum) {
                    winnings.put(selectedTicket, p.amount);
                    p.count--;
                    prizeIdToDecrement.merge(p.id, 1, Integer::sum);
                    break;
                }
            }
        }

        return new DrawResult(winnings, prizeIdToDecrement);
    }

    public void awardTickets(Long chatId, Long amount) {
        Optional<UserBalance> balanceOpt = userBalanceRepository.findById(chatId);
        UserBalance balance;
        if (balanceOpt.isPresent()) {
            balance = balanceOpt.get();
        } else {
            // Double-check it doesn't exist (prevent race condition)
            if (userBalanceRepository.existsById(chatId)) {
                // Entity exists but findById returned empty - fetch again
                balance = userBalanceRepository.findById(chatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance exists but not accessible for chatId: " + chatId));
            } else {
                // Truly doesn't exist - safe to create
                balance = UserBalance.builder()
                    .chatId(chatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                    .build();
                balance = userBalanceRepository.save(balance);
                logger.info("Created new UserBalance for chatId {}", chatId);
            }
        }
        Long tickets = amount;
        balance.setTickets(balance.getTickets() + tickets);
        userBalanceRepository.save(balance);
        logger.info("Awarded {} tickets to chatId {}", tickets, chatId);
    }

    @Transactional
    public void deleteTickets(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        balance.setTickets(0L);
        userBalanceRepository.save(balance);
        logger.info("Deleted all tickets for chatId {}", chatId);
    }

    @Transactional
    public void deleteBalance(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        balance.setBalance(BigDecimal.ZERO);
        userBalanceRepository.save(balance);
        logger.info("Reset balance for chatId {}", chatId);
    }

    @Transactional
    public Map<Long, BigDecimal> playLotteryWithDetails(Long chatId, Long numberOfPlays) {
        UserBalance balance = userBalanceRepository.findByIdWithLock(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        Long minTickets = configurationService.getMinTickets();
        Long maxTickets = configurationService.getMaxTickets();
        if (balance.getTickets() < numberOfPlays || numberOfPlays < minTickets || numberOfPlays > maxTickets) {
            throw new IllegalStateException(String.format("Invalid ticket count: %d. Must be between %d and %d",
                    balance.getTickets(), minTickets, maxTickets));
        }

        List<LotteryPrize> prizes = lotteryPrizeRepository.findAll();
        if (prizes.isEmpty()) {
            throw new IllegalStateException("No lottery prizes configured");
        }
        List<PrizeState> prizeState = buildPrizeState(prizes);
        if (prizeState.isEmpty()) {
            logger.error("No valid prizes with non-zero amount and available count for chatId {}.", chatId);
            throw new IllegalStateException(
                    languageSessionService.getTranslation(chatId, "lottery.message.no_valid_prizes"));
        }

        DrawResult result = drawInMemory(numberOfPlays, prizeState);
        Map<Long, BigDecimal> winnings = result.winnings();

        for (Map.Entry<Long, Integer> e : result.prizeIdToDecrement().entrySet()) {
            lotteryPrizeRepository.decrementNumberOfPrize(e.getKey(), e.getValue());
        }

        BigDecimal totalWinnings = winnings.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        balance.setTickets(balance.getTickets() - numberOfPlays);
        balance.setBalance(balance.getBalance().add(totalWinnings));
        userBalanceRepository.save(balance);
        if (totalWinnings.compareTo(BigDecimal.valueOf(3600)) >= 0) {
            lottoBotService.logWin(numberOfPlays, chatId, totalWinnings);
        }
        logger.info("Played {} tickets for chatId {}, won {} times with total {} UZS", numberOfPlays, chatId,
                winnings.size(), totalWinnings);
        return winnings;
    }

    @Transactional
    public Map<Long, BigDecimal> playLotteryWithDetailsLottoBot(Long chatId, Long numberOfPlays) {
        List<LotteryPrize> prizes = lotteryPrizeRepository.findAll();
        if (prizes.isEmpty()) {
            throw new IllegalStateException("No lottery prizes configured");
        }
        List<PrizeState> prizeState = buildPrizeState(prizes);
        if (prizeState.isEmpty()) {
            logger.error("No valid prizes with non-zero amount and available count for chatId {}.", chatId);
            throw new IllegalStateException(
                    languageSessionService.getTranslation(chatId, "lottery.message.no_valid_prizes"));
        }

        DrawResult result = drawInMemory(numberOfPlays, prizeState);
        for (Map.Entry<Long, Integer> e : result.prizeIdToDecrement().entrySet()) {
            lotteryPrizeRepository.decrementNumberOfPrize(e.getKey(), e.getValue());
        }
        return result.winnings();
    }

    public UserBalance getBalance(Long chatId) {
        return userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
    }

    @Async("bonusProcessingExecutor")
    @Transactional
    public CompletableFuture<Void> awardRandomUsers(
            Long totalUsers, 
            Long randomUsers, 
            Long amount,
            Long adminChatId,
            AdminBotMessageSender adminMessageSender
    ) {
        try {
            if (randomUsers > totalUsers || totalUsers <= 0 || randomUsers <= 0 || amount <= 0) {
                throw new IllegalStateException("Invalid parameters: totalUsers=" + totalUsers + ", randomUsers="
                        + randomUsers + ", amount=" + amount);
            }

            // Fetch last 'totalUsers' approved requests, ordered by creation time, with
            // limit in query
            Pageable pageable = PageRequest.of(0, totalUsers.intValue(), Sort.by(Sort.Direction.DESC, "createdAt"));
            List<HizmatRequest> requests = hizmatRequestRepository.findByFilters(RequestStatus.APPROVED, pageable);

            if (requests.size() < randomUsers) {
                throw new IllegalStateException(
                        "Not enough approved users: requested=" + randomUsers + ", available=" + requests.size());
            }

            // Get unique chat IDs
            List<Long> chatIds = requests.stream()
                    .map(HizmatRequest::getChatId)
                    .distinct()
                    .collect(Collectors.toList());

            if (chatIds.size() < randomUsers) {
                throw new IllegalStateException(
                        "Not enough unique approved users: requested=" + randomUsers + ", available=" + chatIds.size());
            }

            // Randomly select 'randomUsers' chat IDs
            Collections.shuffle(chatIds, random);
            List<Long> selectedChatIds = chatIds.subList(0, randomUsers.intValue());

            BigDecimal awardAmount = new BigDecimal(amount);

            // Process in batches
            int batchSize = 50;
            int totalBatches = (int) Math.ceil((double) selectedChatIds.size() / batchSize);
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < selectedChatIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, selectedChatIds.size());
                List<Long> batch = selectedChatIds.subList(i, end);
                
                // Process batch
                for (Long chatId : batch) {
                    try {
                        processUserAward(chatId, awardAmount, amount);
                        successCount++;
                    } catch (Exception e) {
                        logger.error("Failed to award/notify chatId {}: {}", chatId, e.getMessage());
                        failureCount++;
                    }
                }
                
                // Send progress update
                int currentBatch = (i / batchSize) + 1;
                int progress = (currentBatch * 100) / totalBatches;
                if (adminMessageSender != null && adminChatId != null) {
                    sendProgressUpdate(adminChatId, progress, currentBatch, totalBatches, 
                            successCount, failureCount, adminMessageSender);
                }
            }

            logger.info("Completed awarding bonuses. Success: {}, Failures: {}", successCount, failureCount);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Error in awardRandomUsers", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Transactional
    private void processUserAward(Long chatId, BigDecimal awardAmount, Long amount) {
        // Safe pattern: check if balance exists, only create if truly doesn't exist
        Optional<UserBalance> balanceOpt = userBalanceRepository.findById(chatId);
        UserBalance balance;
        
        if (balanceOpt.isPresent()) {
            balance = balanceOpt.get();
        } else {
            // Double-check to prevent race condition overwrites
            if (userBalanceRepository.existsById(chatId)) {
                balance = userBalanceRepository.findById(chatId)
                        .orElseThrow(() -> new IllegalStateException(
                                "UserBalance exists but not accessible for chatId: " + chatId));
            } else {
                // Truly doesn't exist - safe to create
                balance = UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build();
                balance = userBalanceRepository.save(balance);
                logger.info("Created new UserBalance for chatId {} in processUserAward", chatId);
            }
        }
        
        balance.setBalance(balance.getBalance().add(awardAmount));
        userBalanceRepository.save(balance);

        String messageText = String.format(
                languageSessionService.getTranslation(chatId, "lottery.message.award_notification"),
                amount, balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setReplyMarkup(backButtonKeyboard(chatId));
        messageSender.sendMessage(message, chatId);

        // Safe phone fetch
        Optional<BlockedUser> blockedUserOpt = blockedUserRepository.findByChatId(chatId);
        String number = blockedUserOpt.map(BlockedUser::getPhoneNumber).orElse("N/A");

        adminLogBotService.sendToAdmins("#Кунлик бонусда голиб болганлар\n\n" +
                "Kunlik bonus: " + amount + " \n" +
                "Balans: " + balance.getBalance().longValue() + "\n" +
                "User ID: " + chatId + "\n" +
                "Telefon nomer: " + number + "\n\n" +
                "\uD83D\uDCC5 " + LocalDateTime.now(ZoneId.of("GMT+5"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        logger.info("Awarded {} UZS to chatId {}", amount, chatId);
    }

    private void sendProgressUpdate(Long adminChatId, int progress, int currentBatch, int totalBatches,
                                   int successCount, int failureCount, AdminBotMessageSender adminMessageSender) {
        String progressMessage = String.format(
                "⏳ Mukofot berish jarayoni: %d%%\n" +
                "📦 Batch: %d / %d\n" +
                "✅ Muvaffaqiyatli: %d\n" +
                "❌ Xato: %d",
                progress, currentBatch, totalBatches, successCount, failureCount);
        
        try {
            adminMessageSender.sendTextMessage(adminChatId, progressMessage);
        } catch (Exception e) {
            logger.error("Failed to send progress update to admin {}", adminChatId, e);
        }
    }

    public List<Long> getAllApprovedUsersChatIds() {
        return hizmatRequestRepository.findDistinctChatIdsByStatusApproved();
    }

    public Page<UserBalance> getUserBalancesPaginated(int page, int size, String sortBy, String sortDirection) {
        Sort.Direction direction = Sort.Direction.fromString(sortDirection.toUpperCase());
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        return userBalanceRepository.findAll(pageable);
    }

    public OverallBalanceTicketsDTO getOverallBalanceAndTickets() {
        List<UserBalance> allBalances = userBalanceRepository.findAll();
        
        BigDecimal totalBalance = allBalances.stream()
                .map(UserBalance::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Long totalTickets = allBalances.stream()
                .map(UserBalance::getTickets)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        
        return new OverallBalanceTicketsDTO(totalBalance, totalTickets);
    }

    private InlineKeyboardMarkup backButtonKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "lottery.button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "lottery.button.home"), "HOME"));
        return buttons;
    }

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }
}