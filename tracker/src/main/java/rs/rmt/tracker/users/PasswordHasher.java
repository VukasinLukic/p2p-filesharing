package rs.rmt.tracker.users;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

/**
 * PBKDF2-HMAC-SHA256 password hashing with a per-user random salt.
 *
 * Plain SHA-256 would be wrong here even for a student project: it is designed to be fast, which
 * is exactly what an offline attacker wants. PBKDF2 ships with the JDK, so this stays consistent
 * with the project's no-external-dependency rule.
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PasswordHasher() {}

    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HEX.formatHex(salt);
    }

    public static String hash(String password, String saltHex) {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), HEX.parseHex(saltHex), ITERATIONS, KEY_LENGTH_BITS);
        try {
            return HEX.formatHex(SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded());
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    /** Constant-time comparison: a byte-by-byte early exit leaks how much of the hash matched. */
    public static boolean verify(String password, String saltHex, String expectedHashHex) {
        if (saltHex == null || expectedHashHex == null) return false;
        String actual = hash(password, saltHex);
        return MessageDigest.isEqual(HEX.parseHex(actual), HEX.parseHex(expectedHashHex));
    }
}
