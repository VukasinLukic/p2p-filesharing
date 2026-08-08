package rs.rmt.tracker.users;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory bearer tokens. Sessions deliberately do NOT survive a tracker restart - the peer
 * registry doesn't either, and forcing a fresh login after a restart is the safer default.
 */
public final class SessionStore {
    public static final long DEFAULT_TTL_MILLIS = 12 * 60 * 60 * 1000L;

    private record Session(String userId, long expiresAtMillis) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final Map<String, Session> sessionsByToken = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public SessionStore() {
        this(DEFAULT_TTL_MILLIS);
    }

    public SessionStore(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String create(String userId) {
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String token = HEX.formatHex(raw);
        sessionsByToken.put(token, new Session(userId, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    /** Returns the userId behind a token, or empty when the token is unknown or expired. */
    public Optional<String> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Session session = sessionsByToken.get(token);
        if (session == null) return Optional.empty();
        if (session.expiresAtMillis() < System.currentTimeMillis()) {
            sessionsByToken.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.userId());
    }

    public void invalidate(String token) {
        if (token != null) sessionsByToken.remove(token);
    }

    /** Drops expired tokens; call periodically so long-running trackers don't leak memory. */
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        int before = sessionsByToken.size();
        sessionsByToken.values().removeIf(s -> s.expiresAtMillis() < now);
        return before - sessionsByToken.size();
    }

    public int activeCount() {
        return sessionsByToken.size();
    }

    /** Extracts the token from an "Authorization: Bearer &lt;token&gt;" header value. */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        String token = authorizationHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
