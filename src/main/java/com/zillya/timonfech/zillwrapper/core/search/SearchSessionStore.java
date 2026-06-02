package com.zillya.timonfech.zillwrapper.core.search;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SearchSessionStore {
    private final Map<String, SearchSession> sessions = new ConcurrentHashMap<>();

    public SearchSession create(SearchSession session) {
        String id = UUID.randomUUID().toString();
        session.setSessionId(id);
        session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        session.setState(SearchSessionState.OPEN);
        sessions.put(id, session);
        return session;
    }

    public Optional<SearchSession> get(String sessionId) {
        SearchSession s = sessions.get(sessionId);
        if (s == null) {
            return Optional.empty();
        }
        if (s.getState() != SearchSessionState.OPEN) {
            return Optional.empty();
        }
        if (s.getExpiresAt() != null && Instant.now().isAfter(s.getExpiresAt())) {
            s.setState(SearchSessionState.EXPIRED);
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public Optional<SearchSession> getRaw(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public List<SearchSession> collectExpiredOpen(Instant now) {
        return sessions.values().stream()
                .filter(s -> s.getState() == SearchSessionState.OPEN)
                .filter(s -> s.getExpiresAt() != null && now.isAfter(s.getExpiresAt()))
                .collect(Collectors.toList());
    }
}
