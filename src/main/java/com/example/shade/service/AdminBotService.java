package com.example.shade.service;

import com.example.shade.bot.AdminBotMessageSender;
import com.example.shade.bot.MessageSender;
import com.example.shade.model.AdminCard;
import com.example.shade.model.Currency;
import com.example.shade.model.LotteryPrize;
import com.example.shade.model.OsonConfig;
import com.example.shade.model.PaymentSystem;
import com.example.shade.model.Platform;
import com.example.shade.model.User;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.AdminCardRepository;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.repository.OsonConfigRepository;
import com.example.shade.repository.PlatformRepository;
import com.example.shade.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBotService {

    @Lazy
    private final AdminBotMessageSender messageSender;
    private final FeatureService featureService;
    private final AdminCardRepository adminCardRepository;
    private final OsonConfigRepository osonConfigRepository;
    private final PlatformRepository platformRepository;
    private final ExchangeRateService exchangeRateService;
    private final LotteryService lotteryService;
    private final LotteryPrizeRepository lotteryPrizeRepository;
    private final UserRepository userRepository;
    private final MessageSender userMessageSender;

    @Value("${telegram.admin.bot.token}")
    private String botToken;

    // Main menu
    public void sendMainMenu(Long chatId) {
        messageSender.sendMainMenu(chatId);
    }

    public void requestInput(Long chatId, String message) {
        messageSender.sendTextMessage(chatId, message);
    }

    // ========== FEATURE TOGGLES ==========
    public void sendFeaturesMenu(Long chatId) {
        messageSender.sendFeaturesMenu(chatId);
    }

    @Transactional
    public void toggleTopUp(Long chatId) {
        try {
            boolean currentStatus = featureService.canPerformTopUp();
            featureService.toggleTopUp(!currentStatus);
            boolean newStatus = !currentStatus;
            String status = newStatus ? "Yoqildi ✅" : "O'chirildi ❌";
            messageSender.sendTextMessage(chatId, "To'ldirish funksiyasi " + status);
            sendFeaturesMenu(chatId);
        } catch (Exception e) {
            log.error("Error toggling topup", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void toggleWithdraw(Long chatId) {
        try {
            boolean currentStatus = featureService.canPerformWithdraw();
            featureService.toggleWithdraw(!currentStatus);
            boolean newStatus = !currentStatus;
            String status = newStatus ? "Yoqildi ✅" : "O'chirildi ❌";
            messageSender.sendTextMessage(chatId, "Yechib olish funksiyasi " + status);
            sendFeaturesMenu(chatId);
        } catch (Exception e) {
            log.error("Error toggling withdraw", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void toggleBonus(Long chatId) {
        try {
            boolean currentStatus = featureService.canPerformBonus();
            featureService.toggleBonus(!currentStatus);
            boolean newStatus = !currentStatus;
            String status = newStatus ? "Yoqildi ✅" : "O'chirildi ❌";
            messageSender.sendTextMessage(chatId, "Bonus funksiyasi " + status);
            sendFeaturesMenu(chatId);
        } catch (Exception e) {
            log.error("Error toggling bonus", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== ADMIN CARDS ==========
    public void sendCardsMenu(Long chatId) {
        messageSender.sendCardsMenu(chatId);
    }

    public void getAllCards(Long chatId) {
        try {
            List<AdminCard> cards = adminCardRepository.findAll();
            if (cards.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 Kartalar mavjud emas");
            } else {
                messageSender.sendCardsList(chatId, cards);
            }
        } catch (Exception e) {
            log.error("Error getting all cards", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void sendOsonConfigsForCards(Long chatId) {
        try {
            List<OsonConfig> configs = osonConfigRepository.findAll();
            if (configs.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 OsonConfig mavjud emas");
            } else {
                messageSender.sendOsonConfigSelection(chatId, configs);
            }
        } catch (Exception e) {
            log.error("Error getting oson configs", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getCardsByOsonConfig(Long chatId, Long osonConfigId) {
        try {
            OsonConfig config = osonConfigRepository.findById(osonConfigId)
                    .orElseThrow(() -> new RuntimeException("OsonConfig topilmadi"));
            List<AdminCard> cards = adminCardRepository.findByOsonConfig(config);

            if (cards.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 Bu OsonConfig uchun kartalar mavjud emas");
            } else {
                messageSender.sendCardsList(chatId, cards);
            }
        } catch (Exception e) {
            log.error("Error getting cards by oson config", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getCardById(Long chatId, String cardIdStr) {
        try {
            Long cardId = Long.parseLong(cardIdStr);
            AdminCard card = adminCardRepository.findById(cardId)
                    .orElseThrow(() -> new RuntimeException("Karta topilmadi"));
            messageSender.sendCardDetails(chatId, card);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error getting card by id", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void sendPaymentSystemSelection(Long chatId) {
        messageSender.sendPaymentSystemSelection(chatId);
    }

    public void sendCurrencySelection(Long chatId) {
        messageSender.sendCurrencySelection(chatId);
    }

    @Transactional
    public void createCard(Long chatId, Map<String, Object> context) {
        try {
            String cardNumber = (String) context.get("cardNumber");
            String ownerName = (String) context.get("ownerName");
            Long balance = Long.parseLong((String) context.get("balance"));
            Long osonConfigId = Long.parseLong((String) context.get("osonConfigId"));
            PaymentSystem paymentSystem = PaymentSystem.valueOf((String) context.get("paymentSystem"));

            OsonConfig osonConfig = osonConfigRepository.findById(osonConfigId)
                    .orElseThrow(() -> new RuntimeException("OsonConfig topilmadi"));

            AdminCard card = new AdminCard();
            card.setCardNumber(cardNumber);
            card.setOwnerName(ownerName);
            card.setBalance(balance);
            card.setOsonConfig(osonConfig);
            card.setPaymentSystem(paymentSystem);
            card.setLastUsed(LocalDateTime.now());

            adminCardRepository.save(card);
            messageSender.sendTextMessage(chatId, "✅ Karta muvaffaqiyatli qo'shildi!\nID: " + card.getId());
            sendCardsMenu(chatId);
        } catch (Exception e) {
            log.error("Error creating card", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void updateCard(Long chatId, Map<String, Object> context) {
        try {
            Long cardId = Long.parseLong((String) context.get("updateCardId"));
            String cardNumber = (String) context.get("cardNumber");
            String ownerName = (String) context.get("ownerName");
            Long balance = Long.parseLong((String) context.get("balance"));

            AdminCard card = adminCardRepository.findById(cardId)
                    .orElseThrow(() -> new RuntimeException("Karta topilmadi"));

            card.setCardNumber(cardNumber);
            card.setOwnerName(ownerName);
            card.setBalance(balance);

            adminCardRepository.save(card);
            messageSender.sendTextMessage(chatId, "✅ Karta muvaffaqiyatli yangilandi!");
            sendCardsMenu(chatId);
        } catch (Exception e) {
            log.error("Error updating card", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteCard(Long chatId, String cardIdStr) {
        try {
            Long cardId = Long.parseLong(cardIdStr);
            adminCardRepository.deleteById(cardId);
            messageSender.sendTextMessage(chatId, "✅ Karta muvaffaqiyatli o'chirildi!");
            sendCardsMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error deleting card", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== PLATFORMS ==========
    public void sendPlatformsMenu(Long chatId) {
        messageSender.sendPlatformsMenu(chatId);
    }

    public void getAllPlatforms(Long chatId) {
        try {
            List<Platform> platforms = platformRepository.findAll();
            if (platforms.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 Platformalar mavjud emas");
            } else {
                messageSender.sendPlatformsList(chatId, platforms);
            }
        } catch (Exception e) {
            log.error("Error getting all platforms", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getPlatformById(Long chatId, String platformIdStr) {
        try {
            Long platformId = Long.parseLong(platformIdStr);
            Platform platform = platformRepository.findById(platformId)
                    .orElseThrow(() -> new RuntimeException("Platforma topilmadi"));
            messageSender.sendPlatformDetails(chatId, platform);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error getting platform by id", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void createPlatform(Long chatId, Map<String, Object> context) {
        try {
            String name = (String) context.get("name");
            String apiKey = (String) context.get("apiKey");
            String login = (String) context.get("login");
            String password = (String) context.get("password");
            String workplaceId = (String) context.get("workplaceId");
            Currency currency = Currency.valueOf((String) context.get("currency"));

            Platform platform = new Platform();
            platform.setName(name);
            platform.setApiKey(apiKey);
            platform.setLogin(login);
            platform.setPassword(password);
            platform.setWorkplaceId(workplaceId);
            platform.setCurrency(currency);

            platformRepository.save(platform);
            messageSender.sendTextMessage(chatId, "✅ Platforma muvaffaqiyatli qo'shildi!\nID: " + platform.getId());
            sendPlatformsMenu(chatId);
        } catch (Exception e) {
            log.error("Error creating platform", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void updatePlatform(Long chatId, Map<String, Object> context) {
        try {
            Long platformId = Long.parseLong((String) context.get("updatePlatformId"));
            String name = (String) context.get("name");
            String apiKey = (String) context.get("apiKey");
            String login = (String) context.get("login");
            String password = (String) context.get("password");
            String workplaceId = (String) context.get("workplaceId");

            Platform platform = platformRepository.findById(platformId)
                    .orElseThrow(() -> new RuntimeException("Platforma topilmadi"));

            platform.setName(name);
            platform.setApiKey(apiKey);
            platform.setLogin(login);
            platform.setPassword(password);
            platform.setWorkplaceId(workplaceId);

            platformRepository.save(platform);
            messageSender.sendTextMessage(chatId, "✅ Platforma muvaffaqiyatli yangilandi!");
            sendPlatformsMenu(chatId);
        } catch (Exception e) {
            log.error("Error updating platform", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deletePlatform(Long chatId, String platformIdStr) {
        try {
            Long platformId = Long.parseLong(platformIdStr);
            platformRepository.deleteById(platformId);
            messageSender.sendTextMessage(chatId, "✅ Platforma muvaffaqiyatli o'chirildi!");
            sendPlatformsMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error deleting platform", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== OSON CONFIG ==========
    public void sendOsonConfigMenu(Long chatId) {
        messageSender.sendOsonConfigMenu(chatId);
    }

    public void getAllOsonConfigs(Long chatId) {
        try {
            List<OsonConfig> configs = osonConfigRepository.findAll();
            if (configs.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 OsonConfig mavjud emas");
            } else {
                messageSender.sendOsonConfigsList(chatId, configs);
            }
        } catch (Exception e) {
            log.error("Error getting all oson configs", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getOsonConfigById(Long chatId, String osonIdStr) {
        try {
            Long osonId = Long.parseLong(osonIdStr);
            OsonConfig config = osonConfigRepository.findById(osonId)
                    .orElseThrow(() -> new RuntimeException("OsonConfig topilmadi"));
            messageSender.sendOsonConfigDetails(chatId, config);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error getting oson config by id", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void saveOsonConfig(Long chatId, Map<String, Object> context) {
        try {
            String phone = (String) context.get("phone");
            String password = (String) context.get("password");
            String apiUrl = (String) context.get("apiUrl");
            String apiKey = (String) context.get("apiKey");
            String deviceId = (String) context.get("deviceId");
            String deviceName = (String) context.get("deviceName");

            OsonConfig config = OsonConfig.builder()
                    .phone(phone)
                    .password(password)
                    .apiUrl(apiUrl)
                    .apiKey(apiKey)
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .primaryConfig(false)
                    .build();

            osonConfigRepository.save(config);
            messageSender.sendTextMessage(chatId, "✅ OsonConfig muvaffaqiyatli qo'shildi!\nID: " + config.getId());
            sendOsonConfigMenu(chatId);
        } catch (Exception e) {
            log.error("Error saving oson config", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void updateOsonConfig(Long chatId, Map<String, Object> context) {
        try {
            Long osonId = Long.parseLong((String) context.get("updateOsonId"));
            String phone = (String) context.get("phone");
            String password = (String) context.get("password");
            String apiUrl = (String) context.get("apiUrl");
            String apiKey = (String) context.get("apiKey");
            String deviceId = (String) context.get("deviceId");
            String deviceName = (String) context.get("deviceName");

            OsonConfig config = osonConfigRepository.findById(osonId)
                    .orElseThrow(() -> new RuntimeException("OsonConfig topilmadi"));

            config.setPhone(phone);
            config.setPassword(password);
            config.setApiUrl(apiUrl);
            config.setApiKey(apiKey);
            config.setDeviceId(deviceId);
            config.setDeviceName(deviceName);

            osonConfigRepository.save(config);
            messageSender.sendTextMessage(chatId, "✅ OsonConfig muvaffaqiyatli yangilandi!");
            sendOsonConfigMenu(chatId);
        } catch (Exception e) {
            log.error("Error updating oson config", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void setPrimaryConfig(Long chatId, String osonIdStr) {
        try {
            Long osonId = Long.parseLong(osonIdStr);

            // Unset all primary configs
            List<OsonConfig> allConfigs = osonConfigRepository.findAll();
            allConfigs.forEach(config -> config.setPrimaryConfig(false));
            osonConfigRepository.saveAll(allConfigs);

            // Set new primary
            OsonConfig config = osonConfigRepository.findById(osonId)
                    .orElseThrow(() -> new RuntimeException("OsonConfig topilmadi"));
            config.setPrimaryConfig(true);
            osonConfigRepository.save(config);

            messageSender.sendTextMessage(chatId, "✅ OsonConfig asosiy qilib belgilandi!");
            sendOsonConfigMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error setting primary config", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteOsonConfig(Long chatId, String osonIdStr) {
        try {
            Long osonId = Long.parseLong(osonIdStr);
            osonConfigRepository.deleteById(osonId);
            messageSender.sendTextMessage(chatId, "✅ OsonConfig muvaffaqiyatli o'chirildi!");
            sendOsonConfigMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error deleting oson config", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== EXCHANGE RATE ==========
    public void sendExchangeRateMenu(Long chatId) {
        messageSender.sendExchangeRateMenu(chatId);
    }

    public void getLatestExchangeRate(Long chatId) {
        try {
            Double rate = exchangeRateService.getLatestRate();
            messageSender.sendTextMessage(chatId, 
                String.format("💱 Joriy valyuta kursi:\n\n1 USD = %.2f UZS", rate));
        } catch (Exception e) {
            log.error("Error getting exchange rate", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void updateExchangeRate(Long chatId, String rateStr) {
        try {
            Double rate = Double.parseDouble(rateStr);
            exchangeRateService.updateRate(rate);
            messageSender.sendTextMessage(chatId, 
                String.format("✅ Valyuta kursi yangilandi!\n\n1 USD = %.2f UZS", rate));
            sendExchangeRateMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri format. Masalan: 12750.50");
        } catch (Exception e) {
            log.error("Error updating exchange rate", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== LOTTERY ==========
    public void sendLotteryMenu(Long chatId) {
        messageSender.sendLotteryMenu(chatId);
    }

    @Transactional
    public void addPrize(Long chatId, Map<String, Object> context) {
        try {
            String name = (String) context.get("prizeName");
            BigDecimal amount = new BigDecimal((String) context.get("prizeAmount"));
            Integer count = Integer.parseInt((String) context.get("prizeCount"));

            LotteryPrize prize = new LotteryPrize();
            prize.setName(name);
            prize.setAmount(amount);
            prize.setNumberOfPrize(count);

            lotteryPrizeRepository.save(prize);
            messageSender.sendTextMessage(chatId, "✅ Sovrin muvaffaqiyatli qo'shildi!\nID: " + prize.getId());
            sendLotteryMenu(chatId);
        } catch (Exception e) {
            log.error("Error adding prize", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getAllPrizes(Long chatId) {
        try {
            List<LotteryPrize> prizes = lotteryPrizeRepository.findAll();
            if (prizes.isEmpty()) {
                messageSender.sendTextMessage(chatId, "📭 Sovrinlar mavjud emas");
            } else {
                StringBuilder text = new StringBuilder("🎁 Sovrinlar ro'yxati:\n\n");
                for (LotteryPrize prize : prizes) {
                    text.append("🆔 ID: ").append(prize.getId()).append("\n");
                    text.append("📛 Nomi: ").append(prize.getName()).append("\n");
                    text.append("💰 Miqdor: ").append(prize.getAmount()).append(" UZS\n");
                    text.append("📊 Soni: ").append(prize.getNumberOfPrize()).append("\n\n");
                }
                messageSender.sendTextMessage(chatId, text.toString());
            }
        } catch (Exception e) {
            log.error("Error getting prizes", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deletePrize(Long chatId, String prizeIdStr) {
        try {
            Long prizeId = Long.parseLong(prizeIdStr);
            lotteryPrizeRepository.deleteById(prizeId);
            messageSender.sendTextMessage(chatId, "✅ Sovrin muvaffaqiyatli o'chirildi!");
            sendLotteryMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri ID format");
        } catch (Exception e) {
            log.error("Error deleting prize", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    public void getBalance(Long chatId, String userChatIdStr) {
        try {
            Long userChatId = Long.parseLong(userChatIdStr);
            UserBalance balance = lotteryService.getBalance(userChatId);
            StringBuilder text = new StringBuilder("💰 Foydalanuvchi balansi:\n\n");
            text.append("👤 Chat ID: ").append(userChatId).append("\n");
            text.append("🎫 Biletlar: ").append(balance.getTickets()).append("\n");
            text.append("💵 Balans: ").append(balance.getBalance()).append(" UZS\n");
            messageSender.sendTextMessage(chatId, text.toString());
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri chat ID format");
        } catch (Exception e) {
            log.error("Error getting balance", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteTickets(Long chatId, String userChatIdStr) {
        try {
            Long userChatId = Long.parseLong(userChatIdStr);
            lotteryService.deleteTickets(userChatId);
            messageSender.sendTextMessage(chatId, "✅ Biletlar muvaffaqiyatli o'chirildi!");
            sendLotteryMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri chat ID format");
        } catch (Exception e) {
            log.error("Error deleting tickets", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteBalance(Long chatId, String userChatIdStr) {
        try {
            Long userChatId = Long.parseLong(userChatIdStr);
            lotteryService.deleteBalance(userChatId);
            messageSender.sendTextMessage(chatId, "✅ Balans muvaffaqiyatli o'chirildi!");
            sendLotteryMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri chat ID format");
        } catch (Exception e) {
            log.error("Error deleting balance", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void addTickets(Long chatId, Map<String, Object> context) {
        try {
            Long userChatId = Long.parseLong((String) context.get("ticketChatId"));
            Long amount = Long.parseLong((String) context.get("ticketAmount"));
            lotteryService.awardTickets(userChatId, amount);
            messageSender.sendTextMessage(chatId, "✅ Biletlar muvaffaqiyatli qo'shildi!");
            sendLotteryMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri format");
        } catch (Exception e) {
            log.error("Error adding tickets", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    @Transactional
    public void awardRandomUsers(Long chatId, Map<String, Object> context) {
        try {
            Long totalUsers = Long.parseLong((String) context.get("totalUsers"));
            Long randomUsers = Long.parseLong((String) context.get("randomUsers"));
            Long amount = Long.parseLong((String) context.get("awardAmount"));
            lotteryService.awardRandomUsers(totalUsers, randomUsers, amount);
            messageSender.sendTextMessage(chatId, "✅ Random foydalanuvchilarga mukofot berildi!");
            sendLotteryMenu(chatId);
        } catch (NumberFormatException e) {
            messageSender.sendTextMessage(chatId, "❌ Noto'g'ri format");
        } catch (Exception e) {
            log.error("Error awarding random users", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    // ========== MESSAGE FORWARDING ==========
    public void requestForwardConfirmation(Long chatId, Message message) {
        messageSender.sendTextMessage(chatId, 
            "📨 Ushbu xabarni barcha foydalanuvchilarga yuborishni xohlaysizmi?\n\n" +
            "Tasdiqlash uchun 'ha' yoki 'yes' deb yozing:");
    }

    @Transactional
    public void confirmAndForwardMessage(Long chatId, Map<String, Object> context) {
        try {
            Message message = (Message) context.get("message");
            List<User> users = userRepository.findAll();

            int successCount = 0;
            int failCount = 0;

            messageSender.sendTextMessage(chatId, "📤 Xabarlar yuborilmoqda...");

            // Use AdminBot's ForwardMessage since it has access to the original message
            DefaultAbsSender adminSender = new DefaultAbsSender(new DefaultBotOptions()) {
                @Override
                public String getBotToken() {
                    return botToken;
                }
            };

            for (User user : users) {
                try {
                    // Forward message directly from AdminBot
                    ForwardMessage forwardMessage = new ForwardMessage();
                    forwardMessage.setChatId(user.getChatId().toString());
                    forwardMessage.setFromChatId(chatId.toString());
                    forwardMessage.setMessageId(message.getMessageId());

                    adminSender.execute(forwardMessage);
                    successCount++;
                    Thread.sleep(50); // Avoid hitting rate limits
                } catch (TelegramApiException e) {
                    // If forward fails, try sending the content directly
                    try {
                        boolean sent = sendMessageContentDirectly(user.getChatId(), message);
                        if (sent) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } catch (Exception ex) {
                        log.error("Failed to send message to user: " + user.getChatId(), ex);
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to send message to user: " + user.getChatId(), e);
                    failCount++;
                }
            }

            messageSender.sendTextMessage(chatId, 
                String.format("✅ Xabar yuborish yakunlandi!\n\n" +
                    "✔️ Muvaffaqiyatli: %d\n" +
                    "❌ Xato: %d\n" +
                    "📊 Jami: %d", successCount, failCount, users.size()));

            // Show main menu after completion
            sendMainMenu(chatId);
        } catch (Exception e) {
            log.error("Error forwarding message", e);
            messageSender.sendTextMessage(chatId, "❌ Xatolik yuz berdi: " + e.getMessage());
            sendMainMenu(chatId);
        }
    }

    private boolean sendMessageContentDirectly(Long targetChatId, Message originalMessage) {
        try {
            // If forward fails, send the message content using the user bot
            return userMessageSender.sendMessageBasedOnType(targetChatId, originalMessage);
        } catch (Exception e) {
            log.error("Error sending message content directly: {}", e.getMessage());
            return false;
        }
    }
}
