package rs.rmt.tracker.testutil;

import java.util.Objects;

/** Minimal assertion helpers - no JUnit/Maven, consistent with the rest of this dependency-free project. */
public final class Assert {
    private Assert() {}

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " - expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertNull(Object value, String message) {
        assertTrue(value == null, message + " - expected null but was <" + value + ">");
    }

    public static void assertNotNull(Object value, String message) {
        assertTrue(value != null, message + " - expected non-null");
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
