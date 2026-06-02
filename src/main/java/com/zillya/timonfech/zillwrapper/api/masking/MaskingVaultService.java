package com.zillya.timonfech.zillwrapper.api.masking;

import com.zillya.timonfech.zillwrapper.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MaskingVaultService {

    @Value("${api.masking.session.inactivity-seconds:1800}")
    private long inactivitySeconds;

    @Value("${api.masking.cleanup.fixed-delay-ms:60000}")
    private long cleanupDelayMs;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionInfo startSession(Long ownerUserId, String purpose) {
        if (ownerUserId == null) {
            throw ApiException.unauthorized("Cannot create masking session without owner");
        }
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        sessions.put(sessionId, new SessionState(ownerUserId, purpose, now, now, false, new ConcurrentHashMap<>()));
        return new SessionInfo(sessionId, inactivitySeconds, "session-bound");
    }

    public SessionInfo touchOwnedSession(String sessionId, Long ownerUserId) {
        SessionState state = requireOwnedActive(sessionId, ownerUserId);
        state.lastAccessAt = Instant.now();
        return new SessionInfo(sessionId, inactivitySeconds, "session-bound");
    }

    public void closeOwnedSession(String sessionId, Long ownerUserId) {
        SessionState state = requireOwnedActive(sessionId, ownerUserId);
        state.closed = true;
        state.lastAccessAt = Instant.now();
    }

    public void put(String sessionId,
                    Long ownerUserId,
                    String token,
                    MaskingFieldType fieldType,
                    String value) {
        SessionState state = requireOwnedActive(sessionId, ownerUserId);
        state.lastAccessAt = Instant.now();
        state.values.put(token, new MaskedValue(fieldType, value, Instant.now()));
    }

    public ResolveResult resolve(String sessionId, Long ownerUserId, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new ResolveResult(Map.of(), List.of(), List.of());
        }
        SessionState state = sessions.get(sessionId);
        if (state == null || state.closed || isExpired(state)) {
            return new ResolveResult(Map.of(), List.of(), tokens);
        }
        if (!Objects.equals(state.ownerUserId, ownerUserId)) {
            throw ApiException.forbidden("Masking session ownership mismatch");
        }
        state.lastAccessAt = Instant.now();

        Map<String, String> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            MaskedValue mv = state.values.get(token);
            if (mv == null) {
                missing.add(token);
                continue;
            }
            resolved.put(token, mv.value);
        }
        return new ResolveResult(resolved, missing, List.of());
    }

    public MaskingRenderContext newRenderContext(String sessionId, Long ownerUserId) {
        requireOwnedActive(sessionId, ownerUserId);
        return new MaskingRenderContext(this, sessionId, ownerUserId);
    }

    @Scheduled(fixedDelayString = "${api.masking.cleanup.fixed-delay-ms:60000}")
    public void cleanupExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().closed || isExpired(e.getValue(), now));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("masking_vault_cleanup removedSessions={}", removed);
        }
    }

    private SessionState requireOwnedActive(String sessionId, Long ownerUserId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw ApiException.badRequest("maskingSessionId is required");
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw ApiException.notFound("Masking session not found");
        }
        if (!Objects.equals(state.ownerUserId, ownerUserId)) {
            throw ApiException.forbidden("Masking session ownership mismatch");
        }
        if (state.closed || isExpired(state)) {
            throw ApiException.forbidden("Masking session is closed or expired");
        }
        return state;
    }

    private boolean isExpired(SessionState state) {
        return isExpired(state, Instant.now());
    }

    private boolean isExpired(SessionState state, Instant now) {
        if (state == null || state.lastAccessAt == null) {
            return true;
        }
        return state.lastAccessAt.plusSeconds(Math.max(1, inactivitySeconds)).isBefore(now);
    }

    public record SessionInfo(String maskingSessionId, long inactivitySeconds, String mode) {
    }

    public record ResolveResult(Map<String, String> resolved, List<String> missing, List<String> expired) {
    }

    public static class MaskingRenderContext {
        private final MaskingVaultService vault;
        private final String sessionId;
        private final Long ownerUserId;
        private final Map<MaskingFieldType, Integer> counters = new EnumMap<>(MaskingFieldType.class);
        private final Map<String, String> tokenMapMeta = new LinkedHashMap<>();

        public MaskingRenderContext(MaskingVaultService vault, String sessionId, Long ownerUserId) {
            this.vault = vault;
            this.sessionId = sessionId;
            this.ownerUserId = ownerUserId;
        }

        public String mask(MaskingFieldType type, String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            int next = counters.merge(type, 1, Integer::sum);
            String token = type.placeholder(next);
            vault.put(sessionId, ownerUserId, token, type, value);
            tokenMapMeta.put(token, type.name().toLowerCase(Locale.ROOT));
            return token;
        }

        public Map<String, String> tokenMapMeta() {
            return Collections.unmodifiableMap(tokenMapMeta);
        }

        public int tokenCount() {
            return tokenMapMeta.size();
        }
    }

    private static class SessionState {
        private final Long ownerUserId;
        private final String purpose;
        private final Instant createdAt;
        private volatile Instant lastAccessAt;
        private volatile boolean closed;
        private final Map<String, MaskedValue> values;

        private SessionState(Long ownerUserId,
                             String purpose,
                             Instant createdAt,
                             Instant lastAccessAt,
                             boolean closed,
                             Map<String, MaskedValue> values) {
            this.ownerUserId = ownerUserId;
            this.purpose = purpose;
            this.createdAt = createdAt;
            this.lastAccessAt = lastAccessAt;
            this.closed = closed;
            this.values = values;
        }
    }

    private record MaskedValue(MaskingFieldType fieldType, String value, Instant createdAt) {
    }
}
