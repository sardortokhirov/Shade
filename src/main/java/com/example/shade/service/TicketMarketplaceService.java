package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.*;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.TicketListingRepository;
import com.example.shade.repository.UserBalanceRepository;
import com.example.shade.util.FeeCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketMarketplaceService {
    private static final Logger logger = LoggerFactory.getLogger(TicketMarketplaceService.class);
    private static final int PAGE_SIZE = 5;
    private static final long[] QTY_PRESETS = {1L, 5L, 10L};

    private final MessageSender messageSender;
    private final UserSessionService sessionService;
    private final LanguageSessionService languageSessionService;
    private final UserBalanceRepository userBalanceRepository;
    private final TicketListingRepository ticketListingRepository;
    private final HizmatRequestRepository requestRepository;
    private final LotteryConfigService lotteryConfigService;
    private final AdminLogBotService adminLogBotService;
    private final BlockedUserRepository blockedUserRepository;

    @Lazy
    @Autowired
    private TicketMarketplaceService self;

    @Lazy
    @Autowired
    private BonusService bonusService;

    public void handleCallback(Long chatId, String callback) {
        logger.info("Lottery trade callback {}: {}", chatId, callback);
        sessionService.clearMessageIds(chatId);

        if (callback.equals("BOZOR") || callback.equals("LOTTERY_TRADE_MENU")
                || callback.equals("LOTTERY_TRADE_MENU:HOME")) {
            boolean fromHome = callback.equals("BOZOR") || callback.endsWith(":HOME");
            sessionService.setUserState(chatId, "LOTTERY_TRADE_MENU");
            if (fromHome) {
                sessionService.setUserData(chatId, "tradeFromHome", "true");
                sessionService.addNavigationState(chatId, "MAIN_MENU");
            } else {
                sessionService.removeUserData(chatId, "tradeFromHome");
                sessionService.addNavigationState(chatId, "BONUS_LOTTERY");
            }
            sendTradeMenu(chatId);
        } else if (callback.equals("LOTTERY_TRADE_BROWSE") || callback.startsWith("LOTTERY_TRADE_BROWSE:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sessionService.setUserState(chatId, "LOTTERY_TRADE_BROWSE");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendBrowseListings(chatId, page, TicketListingSide.SELL);
        } else if (callback.equals("LOTTERY_TRADE_REFRESH") || callback.startsWith("LOTTERY_TRADE_REFRESH:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sendBrowseListings(chatId, page, TicketListingSide.SELL);
        } else if (callback.equals("LOTTERY_TRADE_OFFERS") || callback.startsWith("LOTTERY_TRADE_OFFERS:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFERS");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendBrowseListings(chatId, page, TicketListingSide.BUY_OFFER);
        } else if (callback.equals("LOTTERY_TRADE_OFFERS_REFRESH")
                || callback.startsWith("LOTTERY_TRADE_OFFERS_REFRESH:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sendBrowseListings(chatId, page, TicketListingSide.BUY_OFFER);
        } else if (callback.equals("LOTTERY_TRADE_MY")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_MY");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendMyListings(chatId);
        } else if (callback.equals("LOTTERY_TRADE_SELL")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_QTY");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendSellQuantityPrompt(chatId);
        } else if (callback.equals("LOTTERY_TRADE_OFFER")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFER_QTY");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendOfferQuantityPrompt(chatId);
        } else if (callback.equals("LOTTERY_TRADE_SELL_ALL")) {
            UserBalance balance = getOrCreateBalance(chatId);
            long tickets = balance.getTickets() != null ? balance.getTickets() : 0L;
            handleSellQuantity(chatId, String.valueOf(tickets));
        } else if (callback.startsWith("LOTTERY_TRADE_SELL_QTY:")) {
            handleSellQuantity(chatId, callback.substring("LOTTERY_TRADE_SELL_QTY:".length()));
        } else if (callback.startsWith("LOTTERY_TRADE_SELL_PRICE:")) {
            handleSellPrice(chatId, callback.substring("LOTTERY_TRADE_SELL_PRICE:".length()));
        } else if (callback.startsWith("LOTTERY_TRADE_OFFER_QTY:")) {
            handleOfferQuantity(chatId, callback.substring("LOTTERY_TRADE_OFFER_QTY:".length()));
        } else if (callback.startsWith("LOTTERY_TRADE_OFFER_PRICE:")) {
            handleOfferPrice(chatId, callback.substring("LOTTERY_TRADE_OFFER_PRICE:".length()));
        } else if (callback.startsWith("LOTTERY_TRADE_BUY:")) {
            long listingId = Long.parseLong(callback.split(":")[1]);
            self.buyListing(chatId, listingId);
        } else if (callback.startsWith("LOTTERY_TRADE_FULFILL:")) {
            long listingId = Long.parseLong(callback.split(":")[1]);
            self.fulfillOffer(chatId, listingId);
        } else if (callback.startsWith("LOTTERY_TRADE_CANCEL:")) {
            long listingId = Long.parseLong(callback.split(":")[1]);
            self.cancelListing(chatId, listingId);
        } else {
            sendTradeMenu(chatId);
        }
    }

    public void handleTextInput(Long chatId, String text) {
        String state = sessionService.getUserState(chatId);
        if ("LOTTERY_TRADE_SELL_QTY".equals(state)) {
            handleSellQuantity(chatId, text);
        } else if ("LOTTERY_TRADE_SELL_PRICE".equals(state)) {
            handleSellPrice(chatId, text);
        } else if ("LOTTERY_TRADE_OFFER_QTY".equals(state)) {
            handleOfferQuantity(chatId, text);
        } else if ("LOTTERY_TRADE_OFFER_PRICE".equals(state)) {
            handleOfferPrice(chatId, text);
        } else {
            sendTradeMenu(chatId);
        }
    }

    /**
     * @return true if the bot should send the home menu
     */
    public boolean handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        boolean fromHome = "true".equals(sessionService.getUserData(chatId, "tradeFromHome"));
        if (lastState == null || "MAIN_MENU".equals(lastState)
                || "BONUS_LOTTERY".equals(lastState)) {
            if ("MAIN_MENU".equals(lastState) || (lastState == null && fromHome)) {
                sessionService.removeUserData(chatId, "tradeFromHome");
                return true;
            }
            try {
                bonusService.handleCallback(chatId, "BONUS_LOTTERY");
            } catch (Exception e) {
                logger.warn("Failed to return to lottery menu: {}", e.getMessage());
                sendTradeMenu(chatId);
            }
            return false;
        }
        switch (lastState) {
            case "LOTTERY_TRADE_MENU" -> {
                sessionService.setUserState(chatId, "LOTTERY_TRADE_MENU");
                sendTradeMenu(chatId);
            }
            case "LOTTERY_TRADE_SELL_QTY" -> {
                sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_QTY");
                sendSellQuantityPrompt(chatId);
            }
            case "LOTTERY_TRADE_OFFER_QTY" -> {
                sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFER_QTY");
                sendOfferQuantityPrompt(chatId);
            }
            default -> sendTradeMenu(chatId);
        }
        return false;
    }

    private void sendTradeMenu(Long chatId) {
        UserBalance balance = getOrCreateBalance(chatId);
        long tickets = balance.getTickets() != null ? balance.getTickets() : 0L;
        long wallet = balance.getWalletBalance() != null ? balance.getWalletBalance() : 0L;
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.menu"),
                tickets, wallet));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(
                languageSessionService.getTranslation(chatId, "lottery.trade.button.browse"),
                "LOTTERY_TRADE_BROWSE:0")));
        rows.add(List.of(createButton(
                languageSessionService.getTranslation(chatId, "lottery.trade.button.browse_offers"),
                "LOTTERY_TRADE_OFFERS:0")));
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.sell"),
                        "LOTTERY_TRADE_SELL"),
                createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.offer"),
                        "LOTTERY_TRADE_OFFER")));
        rows.add(List.of(createButton(
                languageSessionService.getTranslation(chatId, "lottery.trade.button.my"),
                "LOTTERY_TRADE_MY")));
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void sendBrowseListings(Long chatId, int page, TicketListingSide side) {
        if (page < 0) {
            page = 0;
        }
        boolean offers = isBuyOfferSide(side);
        Page<TicketListing> listings = ticketListingRepository.findByStatusAndSideOrderByCreatedAtDesc(
                TicketListingStatus.ACTIVE, offers ? TicketListingSide.BUY_OFFER : TicketListingSide.SELL,
                PageRequest.of(page, PAGE_SIZE));
        if (listings.isEmpty() && page > 0) {
            sendBrowseListings(chatId, page - 1, side);
            return;
        }

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String emptyKey = offers ? "lottery.trade.offers_empty" : "lottery.trade.browse_empty";
        String titleKey = offers ? "lottery.trade.offers_title" : "lottery.trade.browse_title";
        String itemKey = offers ? "lottery.trade.browse_offer_item" : "lottery.trade.browse_item";
        String pagePrefix = offers ? "LOTTERY_TRADE_OFFERS:" : "LOTTERY_TRADE_BROWSE:";
        String refreshPrefix = offers ? "LOTTERY_TRADE_OFFERS_REFRESH:" : "LOTTERY_TRADE_REFRESH:";

        if (listings.isEmpty()) {
            m.setText(languageSessionService.getTranslation(chatId, emptyKey));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    languageSessionService.getTranslation(chatId, titleKey),
                    page + 1, Math.max(listings.getTotalPages(), 1)));
            for (TicketListing listing : listings.getContent()) {
                boolean own = chatId.equals(listing.getSellerChatId());
                sb.append(String.format(
                        languageSessionService.getTranslation(chatId, itemKey),
                        listing.getId(),
                        listing.getTicketQuantity(),
                        listing.getTotalPrice()));
                if (own) {
                    rows.add(List.of(createButton(
                            String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.cancel_item"),
                                    listing.getId()),
                            "LOTTERY_TRADE_CANCEL:" + listing.getId())));
                } else if (offers) {
                    rows.add(List.of(createButton(
                            String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.fulfill_item"),
                                    listing.getId(), listing.getTotalPrice()),
                            "LOTTERY_TRADE_FULFILL:" + listing.getId())));
                } else {
                    rows.add(List.of(createButton(
                            String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.buy_item"),
                                    listing.getId(), listing.getTotalPrice()),
                            "LOTTERY_TRADE_BUY:" + listing.getId())));
                }
            }
            m.setText(sb.toString());
        }
        m.enableMarkdown(true);

        List<InlineKeyboardButton> pageRow = new ArrayList<>();
        if (page > 0) {
            pageRow.add(createButton("◀️", pagePrefix + (page - 1)));
        }
        pageRow.add(createButton(
                languageSessionService.getTranslation(chatId, "lottery.trade.button.refresh"),
                refreshPrefix + page));
        if (page + 1 < listings.getTotalPages()) {
            pageRow.add(createButton("▶️", pagePrefix + (page + 1)));
        }
        rows.add(pageRow);
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void sendMyListings(Long chatId) {
        List<TicketListing> mine = ticketListingRepository
                .findBySellerChatIdAndStatusOrderByCreatedAtDesc(chatId, TicketListingStatus.ACTIVE);
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (mine.isEmpty()) {
            m.setText(languageSessionService.getTranslation(chatId, "lottery.trade.my_empty"));
        } else {
            StringBuilder sb = new StringBuilder(
                    languageSessionService.getTranslation(chatId, "lottery.trade.my_title"));
            for (TicketListing listing : mine) {
                String itemKey = isBuyOffer(listing)
                        ? "lottery.trade.my_item_offer"
                        : "lottery.trade.my_item_sell";
                sb.append(String.format(
                        languageSessionService.getTranslation(chatId, itemKey),
                        listing.getId(),
                        listing.getTicketQuantity(),
                        listing.getTotalPrice()));
                rows.add(List.of(createButton(
                        String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.cancel_item"),
                                listing.getId()),
                        "LOTTERY_TRADE_CANCEL:" + listing.getId())));
            }
            m.setText(sb.toString());
        }
        m.enableMarkdown(true);
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void sendSellQuantityPrompt(Long chatId) {
        UserBalance balance = getOrCreateBalance(chatId);
        long tickets = balance.getTickets() != null ? balance.getTickets() : 0L;
        long minPrice = lotteryConfigService.getP2pMinPricePerTicket();
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.sell_enter_qty"),
                tickets, minPrice));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        addQtyPresetRows(rows, chatId, tickets, "LOTTERY_TRADE_SELL_QTY:");
        if (tickets > 0) {
            rows.add(List.of(createButton(
                    String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.qty_all"), tickets),
                    "LOTTERY_TRADE_SELL_ALL")));
        }
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void sendOfferQuantityPrompt(Long chatId) {
        UserBalance balance = getOrCreateBalance(chatId);
        long wallet = balance.getWalletBalance() != null ? balance.getWalletBalance() : 0L;
        long minPrice = lotteryConfigService.getP2pMinPricePerTicket();
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.offer_enter_qty"),
                wallet, minPrice));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        addQtyPresetRows(rows, chatId, Long.MAX_VALUE, "LOTTERY_TRADE_OFFER_QTY:");
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void addQtyPresetRows(List<List<InlineKeyboardButton>> rows, Long chatId, long available,
            String callbackPrefix) {
        List<InlineKeyboardButton> qtyRow = new ArrayList<>();
        for (long preset : QTY_PRESETS) {
            if (available >= preset) {
                qtyRow.add(createButton(
                        String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.qty"), preset),
                        callbackPrefix + preset));
            }
        }
        if (!qtyRow.isEmpty()) {
            rows.add(qtyRow);
        }
    }

    private void handleSellQuantity(Long chatId, String text) {
        try {
            long qty = Long.parseLong(text.replaceAll("[^\\d]", ""));
            if (qty < 1) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "lottery.trade.invalid_qty"));
                return;
            }
            UserBalance balance = getOrCreateBalance(chatId);
            long available = balance.getTickets() != null ? balance.getTickets() : 0L;
            if (available < qty) {
                messageSender.sendMessage(chatId, String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_tickets"),
                        available));
                return;
            }
            sessionService.setUserData(chatId, "tradeSellQty", String.valueOf(qty));
            sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_PRICE");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_SELL_QTY");
            sendPricePrompt(chatId, qty, "lottery.trade.sell_enter_price", "LOTTERY_TRADE_SELL_PRICE:");
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_qty"));
        }
    }

    private void handleOfferQuantity(Long chatId, String text) {
        try {
            long qty = Long.parseLong(text.replaceAll("[^\\d]", ""));
            if (qty < 1) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "lottery.trade.invalid_qty"));
                return;
            }
            sessionService.setUserData(chatId, "tradeOfferQty", String.valueOf(qty));
            sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFER_PRICE");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_OFFER_QTY");
            sendPricePrompt(chatId, qty, "lottery.trade.offer_enter_price", "LOTTERY_TRADE_OFFER_PRICE:");
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_qty"));
        }
    }

    private void sendPricePrompt(Long chatId, long qty, String textKey, String priceCallbackPrefix) {
        long minTotal = qty * lotteryConfigService.getP2pMinPricePerTicket();
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, textKey),
                qty, minTotal));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (long price : suggestedTotals(qty, lotteryConfigService.getP2pMinPricePerTicket())) {
            rows.add(List.of(createButton(
                    String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.price"), price),
                    priceCallbackPrefix + price)));
        }
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    static List<Long> suggestedTotals(long qty, long minPerTicket) {
        long minTotal = Math.multiplyExact(qty, minPerTicket);
        Set<Long> prices = new LinkedHashSet<>();
        prices.add(minTotal);
        long round1k = ceilTo(minTotal, 1000L);
        if (round1k <= minTotal) {
            round1k = Math.addExact(minTotal, 1000L);
        }
        prices.add(round1k);
        long round5k = ceilTo(minTotal, 5000L);
        if (round5k <= minTotal) {
            round5k = Math.addExact(minTotal, 5000L);
        }
        prices.add(round5k);
        List<Long> result = new ArrayList<>();
        for (Long p : prices) {
            result.add(p);
            if (result.size() == 3) {
                break;
            }
        }
        return result;
    }

    private static long ceilTo(long value, long step) {
        if (step <= 0) {
            return value;
        }
        long q = (value + step - 1) / step;
        return Math.multiplyExact(q, step);
    }

    private void handleSellPrice(Long chatId, String text) {
        try {
            long price = Long.parseLong(text.replaceAll("[^\\d]", ""));
            Optional<String> qtyOpt = sessionService.beginOneShot(
                    chatId, "LOTTERY_TRADE_SELL_PRICE", "LOTTERY_TRADE_SELL_CREATING", "tradeSellQty");
            if (qtyOpt.isEmpty()) {
                return;
            }
            String qtyStr = qtyOpt.get();
            long qty = Long.parseLong(qtyStr);
            long minPerTicket = lotteryConfigService.getP2pMinPricePerTicket();
            long minTotal = Math.multiplyExact(qty, minPerTicket);
            if (price < minTotal) {
                sessionService.setUserData(chatId, "tradeSellQty", qtyStr);
                sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_PRICE");
                messageSender.sendMessage(chatId, String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.price_too_low"),
                        minTotal));
                return;
            }
            if (!self.createListing(chatId, qty, price)) {
                sessionService.setUserData(chatId, "tradeSellQty", qtyStr);
                sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_PRICE");
            }
        } catch (NumberFormatException | ArithmeticException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_price"));
        }
    }

    private void handleOfferPrice(Long chatId, String text) {
        try {
            long price = Long.parseLong(text.replaceAll("[^\\d]", ""));
            Optional<String> qtyOpt = sessionService.beginOneShot(
                    chatId, "LOTTERY_TRADE_OFFER_PRICE", "LOTTERY_TRADE_OFFER_CREATING", "tradeOfferQty");
            if (qtyOpt.isEmpty()) {
                return;
            }
            String qtyStr = qtyOpt.get();
            long qty = Long.parseLong(qtyStr);
            long minPerTicket = lotteryConfigService.getP2pMinPricePerTicket();
            long minTotal = Math.multiplyExact(qty, minPerTicket);
            if (price < minTotal) {
                sessionService.setUserData(chatId, "tradeOfferQty", qtyStr);
                sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFER_PRICE");
                messageSender.sendMessage(chatId, String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.price_too_low"),
                        minTotal));
                return;
            }
            if (!self.createBuyOffer(chatId, qty, price)) {
                sessionService.setUserData(chatId, "tradeOfferQty", qtyStr);
                sessionService.setUserState(chatId, "LOTTERY_TRADE_OFFER_PRICE");
            }
        } catch (NumberFormatException | ArithmeticException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_price"));
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean createListing(Long chatId, long quantity, long totalPrice) {
        long minPerTicket = lotteryConfigService.getP2pMinPricePerTicket();
        if (quantity < 1 || minPerTicket < 1
                || quantity > Long.MAX_VALUE / minPerTicket
                || totalPrice < quantity * minPerTicket) {
            long minTotal = (quantity < 1 || minPerTicket < 1 || quantity > Long.MAX_VALUE / Math.max(minPerTicket, 1))
                    ? minPerTicket
                    : quantity * minPerTicket;
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.price_too_low"),
                    minTotal));
            return false;
        }
        UserBalance seller = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (seller == null) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_tickets"));
            return false;
        }
        long available = seller.getTickets() != null ? seller.getTickets() : 0L;
        if (available < quantity) {
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_tickets"),
                    available));
            return false;
        }
        seller.setTickets(Math.subtractExact(available, quantity));
        userBalanceRepository.save(seller);

        TicketListing listing = TicketListing.builder()
                .sellerChatId(chatId)
                .side(TicketListingSide.SELL)
                .ticketQuantity(quantity)
                .totalPrice(totalPrice)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                .build();
        ticketListingRepository.save(listing);

        sessionService.removeUserData(chatId, "tradeSellQty");
        sessionService.setUserState(chatId, "LOTTERY_TRADE_MENU");

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.sell_success"),
                listing.getId(), quantity, totalPrice, seller.getTickets()));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.my"),
                        "LOTTERY_TRADE_MY")),
                navRow(chatId)));
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
        return true;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean createBuyOffer(Long chatId, long quantity, long totalPrice) {
        long minPerTicket = lotteryConfigService.getP2pMinPricePerTicket();
        if (quantity < 1 || minPerTicket < 1
                || quantity > Long.MAX_VALUE / minPerTicket
                || totalPrice < quantity * minPerTicket) {
            long minTotal = (quantity < 1 || minPerTicket < 1 || quantity > Long.MAX_VALUE / Math.max(minPerTicket, 1))
                    ? minPerTicket
                    : quantity * minPerTicket;
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.price_too_low"),
                    minTotal));
            return false;
        }
        UserBalance buyer = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (buyer == null) {
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_wallet"),
                    0L, totalPrice));
            return false;
        }
        long wallet = buyer.getWalletBalance() != null ? buyer.getWalletBalance() : 0L;
        if (wallet < totalPrice) {
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_wallet"),
                    wallet, totalPrice));
            return false;
        }
        buyer.setWalletBalance(Math.subtractExact(wallet, totalPrice));
        userBalanceRepository.save(buyer);

        TicketListing listing = TicketListing.builder()
                .sellerChatId(chatId)
                .side(TicketListingSide.BUY_OFFER)
                .ticketQuantity(quantity)
                .totalPrice(totalPrice)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                .build();
        ticketListingRepository.save(listing);

        sessionService.removeUserData(chatId, "tradeOfferQty");
        sessionService.setUserState(chatId, "LOTTERY_TRADE_MENU");

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.offer_success"),
                listing.getId(), quantity, totalPrice, buyer.getWalletBalance()));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.my"),
                        "LOTTERY_TRADE_MY")),
                navRow(chatId)));
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
        return true;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void buyListing(Long buyerChatId, long listingId) {
        TicketListing listing = ticketListingRepository.findByIdWithLock(listingId).orElse(null);
        if (listing == null || listing.getStatus() != TicketListingStatus.ACTIVE || isBuyOffer(listing)) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.listing_unavailable"));
            sendBrowseListings(buyerChatId, 0, TicketListingSide.SELL);
            return;
        }
        Long sellerChatId = listing.getSellerChatId();
        if (buyerChatId.equals(sellerChatId)) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.cannot_buy_own"));
            return;
        }
        if (isChatBlocked(buyerChatId) || isChatBlocked(sellerChatId)) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.blocked"));
            return;
        }

        long price = listing.getTotalPrice();
        BigDecimal feePct = lotteryConfigService.getP2pFeePercentage();
        long fee;
        long net;
        try {
            fee = FeeCalculator.feeAmount(price, feePct);
            net = price - fee;
        } catch (IllegalArgumentException e) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.fee_invalid"));
            return;
        }

        long firstId = Math.min(buyerChatId, sellerChatId);
        long secondId = Math.max(buyerChatId, sellerChatId);
        UserBalance first = userBalanceRepository.findByIdWithLock(firstId).orElse(null);
        UserBalance second = userBalanceRepository.findByIdWithLock(secondId).orElse(null);
        if (first == null || second == null) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.listing_unavailable"));
            return;
        }
        UserBalance buyer = buyerChatId.equals(first.getChatId()) ? first : second;
        UserBalance seller = sellerChatId.equals(first.getChatId()) ? first : second;

        long buyerWallet = buyer.getWalletBalance() != null ? buyer.getWalletBalance() : 0L;
        if (buyerWallet < price) {
            messageSender.sendMessage(buyerChatId, String.format(
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.insufficient_wallet"),
                    buyerWallet, price));
            return;
        }
        if (listing.getStatus() != TicketListingStatus.ACTIVE) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.listing_unavailable"));
            return;
        }

        buyer.setWalletBalance(Math.subtractExact(buyerWallet, price));
        long sellerWallet = seller.getWalletBalance() != null ? seller.getWalletBalance() : 0L;
        seller.setWalletBalance(Math.addExact(sellerWallet, net));
        long buyerTickets = buyer.getTickets() != null ? buyer.getTickets() : 0L;
        buyer.setTickets(Math.addExact(buyerTickets, listing.getTicketQuantity()));
        userBalanceRepository.save(buyer);
        userBalanceRepository.save(seller);

        listing.setStatus(TicketListingStatus.SOLD);
        listing.setBuyerChatId(buyerChatId);
        listing.setFeeAmount(fee);
        listing.setNetAmount(net);
        listing.setSoldAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        ticketListingRepository.save(listing);

        HizmatRequest request = saveTicketTradeRequest(
                buyerChatId, sellerChatId, listing, price, fee, net, buyer.getWalletBalance());

        final Long requestId = request.getId();
        final Long listingIdFinal = listing.getId();
        final long qty = listing.getTicketQuantity();
        final long buyerTicketsFinal = buyer.getTickets();
        final long feeFinal = fee;
        final long netFinal = net;
        final long priceFinal = price;
        final Long sellerFinal = sellerChatId;
        runAfterCommit(() -> notifyTicketTradeSold(
                buyerChatId, sellerFinal, requestId, listingIdFinal, qty, priceFinal, feeFinal, netFinal,
                buyerTicketsFinal, TicketListingSide.SELL));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void fulfillOffer(Long sellerChatId, long listingId) {
        TicketListing listing = ticketListingRepository.findByIdWithLock(listingId).orElse(null);
        if (listing == null || listing.getStatus() != TicketListingStatus.ACTIVE || !isBuyOffer(listing)) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.listing_unavailable"));
            sendBrowseListings(sellerChatId, 0, TicketListingSide.BUY_OFFER);
            return;
        }
        Long buyerChatId = listing.getSellerChatId();
        if (sellerChatId.equals(buyerChatId)) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.cannot_fulfill_own"));
            return;
        }
        if (isChatBlocked(buyerChatId) || isChatBlocked(sellerChatId)) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.blocked"));
            return;
        }

        long price = listing.getTotalPrice();
        BigDecimal feePct = lotteryConfigService.getP2pFeePercentage();
        long fee;
        long net;
        try {
            fee = FeeCalculator.feeAmount(price, feePct);
            net = price - fee;
        } catch (IllegalArgumentException e) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.fee_invalid"));
            return;
        }

        long firstId = Math.min(buyerChatId, sellerChatId);
        long secondId = Math.max(buyerChatId, sellerChatId);
        UserBalance first = userBalanceRepository.findByIdWithLock(firstId).orElse(null);
        UserBalance second = userBalanceRepository.findByIdWithLock(secondId).orElse(null);
        if (first == null || second == null) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.listing_unavailable"));
            return;
        }
        UserBalance buyer = buyerChatId.equals(first.getChatId()) ? first : second;
        UserBalance seller = sellerChatId.equals(first.getChatId()) ? first : second;

        long sellerTickets = seller.getTickets() != null ? seller.getTickets() : 0L;
        if (sellerTickets < listing.getTicketQuantity()) {
            messageSender.sendMessage(sellerChatId, String.format(
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.insufficient_tickets"),
                    sellerTickets));
            return;
        }
        if (listing.getStatus() != TicketListingStatus.ACTIVE) {
            messageSender.sendMessage(sellerChatId,
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.listing_unavailable"));
            return;
        }

        seller.setTickets(Math.subtractExact(sellerTickets, listing.getTicketQuantity()));
        long buyerTickets = buyer.getTickets() != null ? buyer.getTickets() : 0L;
        buyer.setTickets(Math.addExact(buyerTickets, listing.getTicketQuantity()));
        long sellerWallet = seller.getWalletBalance() != null ? seller.getWalletBalance() : 0L;
        seller.setWalletBalance(Math.addExact(sellerWallet, net));
        userBalanceRepository.save(buyer);
        userBalanceRepository.save(seller);

        listing.setStatus(TicketListingStatus.SOLD);
        listing.setBuyerChatId(sellerChatId);
        listing.setFeeAmount(fee);
        listing.setNetAmount(net);
        listing.setSoldAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        ticketListingRepository.save(listing);

        HizmatRequest request = saveTicketTradeRequest(
                buyerChatId, sellerChatId, listing, price, fee, net, seller.getWalletBalance());

        final Long requestId = request.getId();
        final Long listingIdFinal = listing.getId();
        final long qty = listing.getTicketQuantity();
        final long sellerTicketsFinal = seller.getTickets();
        final long buyerTicketsFinal = buyer.getTickets();
        final long feeFinal = fee;
        final long netFinal = net;
        final long priceFinal = price;
        final Long buyerFinal = buyerChatId;
        runAfterCommit(() -> notifyOfferFulfilled(
                buyerFinal, sellerChatId, requestId, listingIdFinal, qty, priceFinal, feeFinal, netFinal,
                buyerTicketsFinal, sellerTicketsFinal));
    }

    private HizmatRequest saveTicketTradeRequest(Long buyerChatId, Long sellerChatId, TicketListing listing,
            long price, long fee, long net, long walletAtTime) {
        HizmatRequest request = new HizmatRequest();
        request.setChatId(buyerChatId);
        request.setRecipientChatId(sellerChatId);
        request.setAmount(price);
        request.setUniqueAmount(price);
        request.setFeeAmount(fee);
        request.setNetAmount(net);
        request.setPlatform("TicketTrade");
        request.setFullName("TICKET_TRADE");
        request.setPlatformUserId(String.valueOf(listing.getTicketQuantity()));
        request.setType(RequestType.TICKET_TRADE);
        request.setStatus(RequestStatus.APPROVED);
        request.setCurrency(Currency.UZS);
        request.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        request.setWalletBalanceAtTime(walletAtTime);
        requestRepository.save(request);
        return request;
    }

    private void notifyTicketTradeSold(Long buyerChatId, Long sellerChatId, Long requestId, Long listingId,
            long qty, long price, long fee, long net, long buyerTickets, TicketListingSide side) {
        String date = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        SendMessage buyerMsg = new SendMessage();
        buyerMsg.setChatId(buyerChatId.toString());
        buyerMsg.setText(String.format(
                languageSessionService.getTranslation(buyerChatId, "lottery.trade.buy_success"),
                requestId, qty, price, fee, buyerTickets, date));
        buyerMsg.enableMarkdown(true);
        messageSender.sendMessage(buyerMsg, buyerChatId);

        try {
            SendMessage sellerMsg = new SendMessage();
            sellerMsg.setChatId(sellerChatId.toString());
            sellerMsg.setText(String.format(
                    languageSessionService.getTranslation(sellerChatId, "lottery.trade.sold_notify"),
                    requestId, listingId, qty, net, fee, date));
            sellerMsg.enableMarkdown(true);
            messageSender.sendMessage(sellerMsg, sellerChatId);
        } catch (Exception e) {
            logger.warn("Failed to notify ticket seller {}: {}", sellerChatId, e.getMessage());
        }

        sendAdminTradeLog(requestId, listingId, buyerChatId, sellerChatId, qty, price, fee, net, side);
        sendBrowseListings(buyerChatId, 0, TicketListingSide.SELL);
    }

    private void notifyOfferFulfilled(Long buyerChatId, Long sellerChatId, Long requestId, Long listingId,
            long qty, long price, long fee, long net, long buyerTickets, long sellerTickets) {
        String date = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        try {
            SendMessage buyerMsg = new SendMessage();
            buyerMsg.setChatId(buyerChatId.toString());
            buyerMsg.setText(String.format(
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.offer_filled_notify"),
                    requestId, listingId, qty, price, buyerTickets, date));
            buyerMsg.enableMarkdown(true);
            messageSender.sendMessage(buyerMsg, buyerChatId);
        } catch (Exception e) {
            logger.warn("Failed to notify offer owner {}: {}", buyerChatId, e.getMessage());
        }

        SendMessage sellerMsg = new SendMessage();
        sellerMsg.setChatId(sellerChatId.toString());
        sellerMsg.setText(String.format(
                languageSessionService.getTranslation(sellerChatId, "lottery.trade.fulfill_success"),
                requestId, listingId, qty, net, fee, sellerTickets, date));
        sellerMsg.enableMarkdown(true);
        messageSender.sendMessage(sellerMsg, sellerChatId);

        sendAdminTradeLog(requestId, listingId, buyerChatId, sellerChatId, qty, price, fee, net,
                TicketListingSide.BUY_OFFER);
        sendBrowseListings(sellerChatId, 0, TicketListingSide.BUY_OFFER);
    }

    private void sendAdminTradeLog(Long requestId, Long listingId, Long buyerChatId, Long sellerChatId,
            long qty, long price, long fee, long net, TicketListingSide side) {
        String tag = isBuyOfferSide(side) ? "#TicketTradeOffer" : "#TicketTrade";
        String adminLog = String.format(
                "🎟️ %s\n🆔: `%d`\n📦 Listing: `%d`\n👤 Buyer: `%d`\n👤 Seller: `%d`\n🎫 Qty: %d\n💵 Price: %,d\n🏛 Fee: %,d\n✅ Net: %,d\n📅 %s",
                tag, requestId, listingId, buyerChatId, sellerChatId,
                qty, price, fee, net,
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        adminLogBotService.sendLog(adminLog);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void cancelListing(Long chatId, long listingId) {
        TicketListing listing = ticketListingRepository.findByIdWithLock(listingId).orElse(null);
        if (listing == null || listing.getStatus() != TicketListingStatus.ACTIVE) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.listing_unavailable"));
            return;
        }
        if (!chatId.equals(listing.getSellerChatId())) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.not_your_listing"));
            return;
        }
        UserBalance owner = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (owner == null) {
            return;
        }
        boolean offer = isBuyOffer(listing);
        if (offer) {
            long wallet = owner.getWalletBalance() != null ? owner.getWalletBalance() : 0L;
            owner.setWalletBalance(Math.addExact(wallet, listing.getTotalPrice()));
        } else {
            long tickets = owner.getTickets() != null ? owner.getTickets() : 0L;
            owner.setTickets(tickets + listing.getTicketQuantity());
        }
        userBalanceRepository.save(owner);
        listing.setStatus(TicketListingStatus.CANCELLED);
        ticketListingRepository.save(listing);

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        if (offer) {
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.cancel_offer_success"),
                    listing.getId(), listing.getTotalPrice(), owner.getWalletBalance()));
        } else {
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.cancel_success"),
                    listing.getId(), listing.getTicketQuantity(), owner.getTickets()));
        }
        m.enableMarkdown(true);
        messageSender.sendMessage(m, chatId);
        sendMyListings(chatId);
    }

    private boolean isBuyOffer(TicketListing listing) {
        return listing != null && isBuyOfferSide(listing.getSide());
    }

    private boolean isBuyOfferSide(TicketListingSide side) {
        return side == TicketListingSide.BUY_OFFER;
    }

    private boolean isChatBlocked(Long chatId) {
        return blockedUserRepository.findByChatId(chatId)
                .map(u -> "BLOCKED".equals(u.getPhoneNumber()))
                .orElse(false);
    }

    private UserBalance getOrCreateBalance(Long chatId) {
        return userBalanceRepository.findById(chatId).orElseGet(() ->
                userBalanceRepository.save(UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .walletBalance(0L)
                        .build()));
    }

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }

    private List<InlineKeyboardButton> navRow(Long chatId) {
        return List.of(
                createButton(languageSessionService.getTranslation(chatId, "button.back"), "BACK"),
                createButton(languageSessionService.getTranslation(chatId, "button.home"), "HOME"));
    }
}
