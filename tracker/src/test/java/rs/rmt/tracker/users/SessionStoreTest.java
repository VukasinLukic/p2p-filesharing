package rs.rmt.tracker.users;

import rs.rmt.tracker.testutil.Assert;

/** Bearer-token sessions: issuing, expiry, invalidation and header parsing. */
public class SessionStoreTest {

    public void testTokenResolvesToItsUser() {
        SessionStore sessions = new SessionStore();
        String token = sessions.create("user-1");

        Assert.assertEquals("user-1", sessions.resolve(token).orElse(null), "token maps back to its user");
        Assert.assertTrue(sessions.resolve("nepostojeci-token").isEmpty(), "unknown token resolves to nothing");
        Assert.assertTrue(sessions.resolve(null).isEmpty(), "null token resolves to nothing");
        Assert.assertTrue(sessions.resolve("").isEmpty(), "blank token resolves to nothing");
    }

    public void testTokensAreUniquePerLogin() {
        SessionStore sessions = new SessionStore();
        Assert.assertFalse(sessions.create("user-1").equals(sessions.create("user-1")),
                "each login gets its own token so logging out of one device leaves the other alone");
    }

    public void testExpiredTokenIsRejectedAndPurged() throws Exception {
        SessionStore sessions = new SessionStore(30); // 30ms TTL
        String token = sessions.create("user-1");
        Thread.sleep(60);

        Assert.assertTrue(sessions.resolve(token).isEmpty(), "expired token must not resolve");
        Assert.assertEquals(0, sessions.activeCount(), "resolving an expired token drops it");
    }

    public void testPurgeExpiredKeepsLiveSessions() throws Exception {
        SessionStore shortLived = new SessionStore(30);
        shortLived.create("user-1");
        Thread.sleep(60);
        Assert.assertEquals(1, shortLived.purgeExpired(), "one expired session is purged");

        SessionStore longLived = new SessionStore();
        longLived.create("user-2");
        Assert.assertEquals(0, longLived.purgeExpired(), "a live session is left alone");
        Assert.assertEquals(1, longLived.activeCount(), "live session is still usable");
    }

    public void testLogoutInvalidatesTheToken() {
        SessionStore sessions = new SessionStore();
        String token = sessions.create("user-1");
        sessions.invalidate(token);
        Assert.assertTrue(sessions.resolve(token).isEmpty(), "invalidated token must stop working");
    }

    public void testBearerTokenParsing() {
        Assert.assertEquals("abc123", SessionStore.bearerToken("Bearer abc123"), "standard header");
        Assert.assertEquals("abc123", SessionStore.bearerToken("bearer abc123"), "scheme is case-insensitive");
        Assert.assertNull(SessionStore.bearerToken("abc123"), "a bare token without the scheme is not accepted");
        Assert.assertNull(SessionStore.bearerToken("Bearer "), "empty token is not accepted");
        Assert.assertNull(SessionStore.bearerToken(null), "missing header is not accepted");
    }
}
