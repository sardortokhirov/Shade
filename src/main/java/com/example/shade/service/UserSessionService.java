package com.example.shade.service;

import com.example.shade.model.UserSession;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {
    private final Map<Long, UserSession> sessionStore = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> sessionDataStore = new ConcurrentHashMap<>();

    public void setUserState(Long chatId, String state) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        synchronized (session) {
            session.setChatId(chatId);
            session.setState(state);
        }
        sessionStore.put(chatId, session);
    }

    /**
     * Atomically move a user from {@code expected} to {@code next}. Used so a Confirm
     * button cannot start two wallet P2P transfers at once.
     */
    public boolean compareAndSetState(Long chatId, String expected, String next) {
        if (chatId == null || expected == null) {
            return false;
        }
        UserSession session = sessionStore.get(chatId);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            if (!expected.equals(session.getState())) {
                return false;
            }
            session.setState(next);
            return true;
        }
    }

    /**
     * One-shot action: only the first caller that sees {@code expected} state wins.
     * Advances state to {@code next} and consumes {@code dataKey} under the same lock
     * so a double-tap cannot start two money/ticket mutations.
     *
     * @return consumed value, or empty if state mismatch or key already consumed
     */
    public Optional<String> beginOneShot(Long chatId, String expected, String next, String dataKey) {
        if (chatId == null || expected == null || dataKey == null) {
            return Optional.empty();
        }
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        synchronized (session) {
            if (!expected.equals(session.getState())) {
                return Optional.empty();
            }
            Map<String, String> data = sessionDataStore.get(chatId);
            String value = data != null ? data.get(dataKey) : null;
            if (value == null) {
                return Optional.empty();
            }
            data.remove(dataKey);
            if (data.isEmpty()) {
                sessionDataStore.remove(chatId);
            }
            session.setChatId(chatId);
            session.setState(next);
            return Optional.of(value);
        }
    }

    public String getUserState(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getState)
                .orElse(null);
    }

    public void setUserData(Long chatId, String key, String value) {
        Map<String, String> data = sessionDataStore.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>());
        data.put(key, value);
        sessionDataStore.put(chatId, data);
    }
    public String getUserData(Long chatId, String key, String defaultValue) {
        return Optional.ofNullable(sessionDataStore.get(chatId))
                .map(data -> data.getOrDefault(key, defaultValue))
                .orElse(defaultValue);
    }

    public String getUserData(Long chatId, String key) {
        return Optional.ofNullable(sessionDataStore.get(chatId))
                .map(data -> data.get(key))
                .orElse(null);
    }

    public void removeUserData(Long chatId, String key) {
        consumeUserData(chatId, key);
    }

    /** Removes and returns the value so a transfer amount can be used only once. */
    public String consumeUserData(Long chatId, String key) {
        Map<String, String> data = sessionDataStore.get(chatId);
        if (data == null) {
            return null;
        }
        String value = data.remove(key);
        if (data.isEmpty()) {
            sessionDataStore.remove(chatId);
        }
        return value;
    }

    public void addNavigationState(Long chatId, String state) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        List<String> navigationStates = session.getNavigationStates();
        navigationStates.add(state);
        session.setNavigationStates(navigationStates);
        sessionStore.put(chatId, session);
    }

    public String popNavigationState(Long chatId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        List<String> navigationStates = session.getNavigationStates();
        if (navigationStates.isEmpty()) {
            return null;
        }
        String lastState = navigationStates.remove(navigationStates.size() - 1);
        session.setNavigationStates(navigationStates);
        sessionStore.put(chatId, session);
        return lastState;
    }

    public void clearSession(Long chatId) {
        sessionStore.remove(chatId);
        sessionDataStore.remove(chatId);
    }

    public List<Integer> getMessageIds(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getMessageIds)
                .orElse(new ArrayList<>());
    }

    public void clearMessageIds(Long chatId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        session.setMessageIds(new ArrayList<>());
        sessionStore.put(chatId, session);
    }

    public Optional<UserSession> getUserSession(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId));
    }

    public void saveUserSession(UserSession session) {
        if (session.getChatId() != null) {
            sessionStore.put(session.getChatId(), session);
        }
    }
}