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
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketMarketplaceService {
    private static final Logger logger = LoggerFactory.getLogger(TicketMarketplaceService.class);
    private static final int PAGE_SIZE = 5;

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

        if (callback.equals("LOTTERY_TRADE_MENU")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_MENU");
            sessionService.addNavigationState(chatId, "BONUS_LOTTERY");
            sendTradeMenu(chatId);
        } else if (callback.equals("LOTTERY_TRADE_BROWSE") || callback.startsWith("LOTTERY_TRADE_BROWSE:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sessionService.setUserState(chatId, "LOTTERY_TRADE_BROWSE");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendBrowseListings(chatId, page);
        } else if (callback.equals("LOTTERY_TRADE_REFRESH") || callback.startsWith("LOTTERY_TRADE_REFRESH:")) {
            int page = callback.contains(":") ? Integer.parseInt(callback.split(":")[1]) : 0;
            sendBrowseListings(chatId, page);
        } else if (callback.equals("LOTTERY_TRADE_MY")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_MY");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendMyListings(chatId);
        } else if (callback.equals("LOTTERY_TRADE_SELL")) {
            sessionService.setUserState(chatId, "LOTTERY_TRADE_SELL_QTY");
            sessionService.addNavigationState(chatId, "LOTTERY_TRADE_MENU");
            sendSellQuantityPrompt(chatId);
        } else if (callback.startsWith("LOTTERY_TRADE_BUY:")) {
            long listingId = Long.parseLong(callback.split(":")[1]);
            self.buyListing(chatId, listingId);
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
        } else {
            sendTradeMenu(chatId);
        }
    }

    public void handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        if (lastState == null || "BONUS_LOTTERY".equals(lastState) || "MAIN_MENU".equals(lastState)) {
            try {
                bonusService.handleCallback(chatId, "BONUS_LOTTERY");
            } catch (Exception e) {
                logger.warn("Failed to return to lottery menu: {}", e.getMessage());
                sendTradeMenu(chatId);
            }
            return;
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
            default -> sendTradeMenu(chatId);
        }
    }

    private void sendTradeMenu(Long chatId) {
        UserBalance balance = getOrCreateBalance(chatId);
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.menu"),
                balance.getTickets() != null ? balance.getTickets() : 0L));
        m.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.browse"),
                        "LOTTERY_TRADE_BROWSE:0")));
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.my"),
                        "LOTTERY_TRADE_MY"),
                createButton(languageSessionService.getTranslation(chatId, "lottery.trade.button.sell"),
                        "LOTTERY_TRADE_SELL")));
        rows.add(navRow(chatId));
        markup.setKeyboard(rows);
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
    }

    private void sendBrowseListings(Long chatId, int page) {
        if (page < 0) {
            page = 0;
        }
        Page<TicketListing> listings = ticketListingRepository.findByStatusOrderByCreatedAtDesc(
                TicketListingStatus.ACTIVE, PageRequest.of(page, PAGE_SIZE));
        if (listings.isEmpty() && page > 0) {
            sendBrowseListings(chatId, page - 1);
            return;
        }

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (listings.isEmpty()) {
            m.setText(languageSessionService.getTranslation(chatId, "lottery.trade.browse_empty"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.browse_title"),
                    page + 1, Math.max(listings.getTotalPages(), 1)));
            for (TicketListing listing : listings.getContent()) {
                boolean own = chatId.equals(listing.getSellerChatId());
                sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.browse_item"),
                        listing.getId(),
                        listing.getTicketQuantity(),
                        listing.getTotalPrice(),
                        listing.getSellerChatId()));
                if (own) {
                    rows.add(List.of(createButton(
                            String.format(languageSessionService.getTranslation(chatId, "lottery.trade.button.cancel_item"),
                                    listing.getId()),
                            "LOTTERY_TRADE_CANCEL:" + listing.getId())));
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
            pageRow.add(createButton("◀️", "LOTTERY_TRADE_BROWSE:" + (page - 1)));
        }
        pageRow.add(createButton(
                languageSessionService.getTranslation(chatId, "lottery.trade.button.refresh"),
                "LOTTERY_TRADE_REFRESH:" + page));
        if (page + 1 < listings.getTotalPages()) {
            pageRow.add(createButton("▶️", "LOTTERY_TRADE_BROWSE:" + (page + 1)));
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
                sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.browse_item"),
                        listing.getId(),
                        listing.getTicketQuantity(),
                        listing.getTotalPrice(),
                        listing.getSellerChatId()));
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
        markup.setKeyboard(List.of(navRow(chatId)));
        m.setReplyMarkup(markup);
        messageSender.sendMessage(m, chatId);
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
            long minTotal = qty * lotteryConfigService.getP2pMinPricePerTicket();
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.sell_enter_price"),
                    qty, minTotal));
            m.enableMarkdown(true);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(navRow(chatId)));
            m.setReplyMarkup(markup);
            messageSender.sendMessage(m, chatId);
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_qty"));
        }
    }

    private void handleSellPrice(Long chatId, String text) {
        try {
            long price = Long.parseLong(text.replaceAll("[^\\d]", ""));
            String qtyStr = sessionService.getUserData(chatId, "tradeSellQty");
            if (qtyStr == null) {
                sendSellQuantityPrompt(chatId);
                return;
            }
            long qty = Long.parseLong(qtyStr);
            long minPerTicket = lotteryConfigService.getP2pMinPricePerTicket();
            if (price < qty * minPerTicket) {
                messageSender.sendMessage(chatId, String.format(
                        languageSessionService.getTranslation(chatId, "lottery.trade.price_too_low"),
                        qty * minPerTicket));
                return;
            }
            self.createListing(chatId, qty, price);
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.invalid_price"));
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void createListing(Long chatId, long quantity, long totalPrice) {
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
            return;
        }
        UserBalance seller = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (seller == null) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_tickets"));
            return;
        }
        long available = seller.getTickets() != null ? seller.getTickets() : 0L;
        if (available < quantity) {
            messageSender.sendMessage(chatId, String.format(
                    languageSessionService.getTranslation(chatId, "lottery.trade.insufficient_tickets"),
                    available));
            return;
        }
        seller.setTickets(available - quantity);
        userBalanceRepository.save(seller);

        TicketListing listing = TicketListing.builder()
                .sellerChatId(chatId)
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
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void buyListing(Long buyerChatId, long listingId) {
        TicketListing listing = ticketListingRepository.findByIdWithLock(listingId).orElse(null);
        if (listing == null || listing.getStatus() != TicketListingStatus.ACTIVE) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.listing_unavailable"));
            sendBrowseListings(buyerChatId, 0);
            return;
        }
        Long sellerChatId = listing.getSellerChatId();
        if (buyerChatId.equals(sellerChatId)) {
            messageSender.sendMessage(buyerChatId,
                    languageSessionService.getTranslation(buyerChatId, "lottery.trade.cannot_buy_own"));
            return;
        }
        if (blockedUserRepository.existsByChatId(buyerChatId)
                || blockedUserRepository.existsByChatId(sellerChatId)) {
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
        request.setWalletBalanceAtTime(buyer.getWalletBalance());
        requestRepository.save(request);

        final Long requestId = request.getId();
        final Long listingIdFinal = listing.getId();
        final long qty = listing.getTicketQuantity();
        final long buyerTicketsFinal = buyer.getTickets();
        final long feeFinal = fee;
        final long netFinal = net;
        final long priceFinal = price;
        final Long sellerFinal = sellerChatId;
        runAfterCommit(() -> notifyTicketTradeSold(
                buyerChatId, sellerFinal, requestId, listingIdFinal, qty, priceFinal, feeFinal, netFinal, buyerTicketsFinal));
    }

    private void notifyTicketTradeSold(Long buyerChatId, Long sellerChatId, Long requestId, Long listingId,
            long qty, long price, long fee, long net, long buyerTickets) {
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

        String adminLog = String.format(
                "🎟️ #TicketTrade\n🆔: `%d`\n📦 Listing: `%d`\n👤 Buyer: `%d`\n👤 Seller: `%d`\n🎫 Qty: %d\n💵 Price: %,d\n🏛 Fee: %,d\n✅ Net: %,d\n📅 %s",
                requestId, listingId, buyerChatId, sellerChatId,
                qty, price, fee, net,
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        adminLogBotService.sendLog(adminLog);
        sendBrowseListings(buyerChatId, 0);
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
        UserBalance seller = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (seller == null) {
            return;
        }
        long tickets = seller.getTickets() != null ? seller.getTickets() : 0L;
        seller.setTickets(tickets + listing.getTicketQuantity());
        userBalanceRepository.save(seller);
        listing.setStatus(TicketListingStatus.CANCELLED);
        ticketListingRepository.save(listing);

        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "lottery.trade.cancel_success"),
                listing.getId(), listing.getTicketQuantity(), seller.getTickets()));
        m.enableMarkdown(true);
        messageSender.sendMessage(m, chatId);
        sendMyListings(chatId);
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
