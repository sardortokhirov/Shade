package com.example.shade.config;

import com.example.shade.bot.AdminLogBot;
import com.example.shade.bot.ApkLinkDistributionBot;
import com.example.shade.bot.LottoLogBot;
import com.example.shade.bot.ShadePaymentBot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Date-6/11/2025
 * By Sardor Tokhirov
 * Time-10:09 AM (GMT+5)
 */
@Configuration
public class BotInitializer {

    private static final Logger logger = LoggerFactory.getLogger(BotInitializer.class);

    @Autowired
    private ShadePaymentBot shadePaymentBot;

    @Autowired
    private AdminLogBot adminLogBot;

    @Autowired
    private LottoLogBot lottoLogBot;

    @Autowired
    private ApkLinkDistributionBot apkLinkDistributionBot;

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(adminLogBot);
            botsApi.registerBot(shadePaymentBot);
            botsApi.registerBot(lottoLogBot);
            String apkLinkToken = apkLinkDistributionBot.getBotToken();
            if (apkLinkToken != null && !apkLinkToken.isEmpty()) {
                botsApi.registerBot(apkLinkDistributionBot);
                logger.info("ApkLink distribution bot registered.");
            } else {
                logger.warn("ApkLink distribution bot token not configured; skipping registration.");
            }
            logger.info("Bots started and registered successfully.");
        } catch (TelegramApiException e) {
            logger.error("Failed to register bots: {}", e.getMessage());
        }
    }
}