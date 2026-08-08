package rs.rmt.tracker.users;

import rs.rmt.tracker.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * File-backed user directory: the whole set of accounts lives in one JSON document
 * (`data/users.json` next to the tracker) that is rewritten on every change.
 *
 * A JSON document rather than SQLite/H2 on purpose - those need a driver JAR, and this project
 * compiles with plain `javac` and no dependency manager. The account count here is measured in
 * dozens, so rewrite-on-change costs nothing and keeps the storage human-readable during a demo.
 * The interface (register/authenticate/find) is the part that matters: swapping the backing store
 * for a real database later touches this class only.
 */
public final class UserStore {
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 32;

    /** Thrown for anything a client did wrong; the message is safe to show to the user. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    private final Path storageFile;
    private final Map<String, User> usersByUsername = new LinkedHashMap<>();
    private final Map<String, User> usersById = new LinkedHashMap<>();

    /** In-memory only - used by tests and by anyone who doesn't want accounts to survive a restart. */
    public UserStore() {
        this(null);
    }

    public UserStore(Path storageFile) {
        this.storageFile = storageFile;
        load();
    }

    public synchronized User register(String username, String password, String displayName) {
        String normalized = normalizeUsername(username);
        validatePassword(password);
        if (usersByUsername.containsKey(normalized)) {
            throw new ValidationException("Korisnicko ime '" + normalized + "' je vec zauzeto");
        }

        String salt = PasswordHasher.newSalt();
        User user = new User(
                UUID.randomUUID().toString(),
                normalized,
                (displayName == null || displayName.isBlank()) ? normalized : displayName.trim(),
                salt,
                PasswordHasher.hash(password, salt),
                System.currentTimeMillis());

        usersByUsername.put(normalized, user);
        usersById.put(user.userId(), user);
        save();
        return user;
    }

    /** Empty when the user doesn't exist OR the password is wrong - callers must not distinguish. */
    public synchronized Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        User user = usersByUsername.get(username.trim().toLowerCase(Locale.ROOT));
        if (user == null) return Optional.empty();
        if (!PasswordHasher.verify(password, user.passwordSalt(), user.passwordHash())) return Optional.empty();
        return Optional.of(user);
    }

    public synchronized Optional<User> findById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public synchronized Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(usersByUsername.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized List<User> all() {
        return new ArrayList<>(usersByUsername.values());
    }

    public synchronized int size() {
        return usersByUsername.size();
    }

    // ---------- Validation ----------

    private static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_USERNAME_LENGTH || normalized.length() > MAX_USERNAME_LENGTH) {
            throw new ValidationException("Korisnicko ime mora imati izmedju " + MIN_USERNAME_LENGTH
                    + " i " + MAX_USERNAME_LENGTH + " karaktera");
        }
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new ValidationException("Korisnicko ime sme da sadrzi samo slova, brojeve i . _ -");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Lozinka mora imati najmanje " + MIN_PASSWORD_LENGTH + " karaktera");
        }
    }

    // ---------- Persistence ----------

    private void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try {
            String text = Files.readString(storageFile, StandardCharsets.UTF_8);
            for (Object entry : Json.parseArray(text)) {
                @SuppressWarnings("unchecked")
                User user = User.fromStorageJson((Map<String, Object>) entry);
                usersByUsername.put(user.username(), user);
                usersById.put(user.userId(), user);
            }
            System.out.println("[Users] loaded " + usersByUsername.size() + " account(s) from " + storageFile);
        } catch (IOException | RuntimeException e) {
            // Refuse to start on a corrupt file rather than silently handing everyone a blank
            // directory and overwriting the accounts on the next register().
            throw new IllegalStateException("Could not read user store " + storageFile + ": " + e.getMessage(), e);
        }
    }

    private void save() {
        if (storageFile == null) return;
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (User user : usersByUsername.values()) serialized.add(user.toStorageJson());

        try {
            Path parent = storageFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            // Write-then-rename: a crash mid-write must not leave a half-written user file behind.
            Path temp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(temp, Json.stringify(serialized), StandardCharsets.UTF_8);
            try {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist user store " + storageFile, e);
        }
    }
}
