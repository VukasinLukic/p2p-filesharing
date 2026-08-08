package rs.rmt.tracker.users;

import rs.rmt.tracker.testutil.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/** Account storage: hashing, validation, duplicate handling and JSON persistence. */
public class UserStoreTest {

    public void testRegisterThenAuthenticate() {
        UserStore store = new UserStore();
        User created = store.register("Milica", "tajna123", "Milica M.");

        Assert.assertEquals("milica", created.username(), "usernames are normalised to lower case");
        Assert.assertEquals("Milica M.", created.displayName(), "display name is kept as typed");
        Assert.assertTrue(store.authenticate("milica", "tajna123").isPresent(), "correct password logs in");
        Assert.assertTrue(store.authenticate("MILICA", "tajna123").isPresent(), "login is case-insensitive");
        Assert.assertTrue(store.authenticate("milica", "pogresna").isEmpty(), "wrong password is rejected");
        Assert.assertTrue(store.authenticate("nepostojeci", "tajna123").isEmpty(), "unknown user is rejected");
    }

    public void testPasswordIsNeverStoredInPlainText() {
        UserStore store = new UserStore();
        User user = store.register("vukasin", "supersifra", null);

        Assert.assertFalse(user.passwordHash().contains("supersifra"), "hash must not contain the password");
        Assert.assertNotNull(user.passwordSalt(), "every account gets a salt");
        Assert.assertFalse(user.toPublicJson().containsKey("passwordHash"), "public JSON must not leak the hash");
        Assert.assertFalse(user.toPublicJson().containsKey("passwordSalt"), "public JSON must not leak the salt");
    }

    public void testSamePasswordProducesDifferentHashesForDifferentUsers() {
        UserStore store = new UserStore();
        User a = store.register("ana", "istalozinka", null);
        User b = store.register("bojan", "istalozinka", null);

        // Per-user salts: otherwise one rainbow table cracks every account with a common password.
        Assert.assertFalse(a.passwordHash().equals(b.passwordHash()),
                "identical passwords must hash differently thanks to per-user salts");
    }

    public void testDuplicateUsernameIsRejected() {
        UserStore store = new UserStore();
        store.register("marko", "lozinka1", null);
        try {
            store.register("MARKO", "lozinka2", null);
            Assert.fail("registering an existing username (any casing) must fail");
        } catch (UserStore.ValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("marko"), "message names the taken username");
        }
        Assert.assertEquals(1, store.size(), "the failed registration must not have been stored");
    }

    public void testInvalidUsernamesAndPasswordsAreRejected() {
        UserStore store = new UserStore();
        assertRejected(store, "ab", "lozinka1", "username shorter than 3 characters");
        assertRejected(store, "ima razmak", "lozinka1", "username with a space");
        assertRejected(store, "ime@domen", "lozinka1", "username with an illegal character");
        assertRejected(store, "validno", "kratk", "password shorter than 6 characters is rejected");
        Assert.assertEquals(0, store.size(), "no invalid account may be stored");
    }

    public void testAccountsSurviveAReload() throws Exception {
        Path dir = Files.createTempDirectory("users-test");
        try {
            Path file = dir.resolve("nested").resolve("users.json");

            UserStore first = new UserStore(file);
            first.register("trajni", "lozinka123", "Trajni Korisnik");
            Assert.assertTrue(Files.exists(file), "store creates its parent directory and file");

            UserStore reloaded = new UserStore(file);
            Assert.assertEquals(1, reloaded.size(), "account is read back from disk");
            Optional<User> user = reloaded.authenticate("trajni", "lozinka123");
            Assert.assertTrue(user.isPresent(), "the reloaded hash still verifies the password");
            Assert.assertEquals("Trajni Korisnik", user.get().displayName(), "profile fields survive");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testCorruptStoreFailsLoudlyInsteadOfWipingAccounts() throws Exception {
        Path dir = Files.createTempDirectory("users-test");
        try {
            Path file = dir.resolve("users.json");
            Files.writeString(file, "{ ovo nije validan JSON");
            try {
                new UserStore(file);
                Assert.fail("a corrupt user file must not silently start with an empty directory");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("users.json"), "error names the bad file");
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void assertRejected(UserStore store, String username, String password, String why) {
        try {
            store.register(username, password, null);
            Assert.fail("must be rejected: " + why);
        } catch (UserStore.ValidationException expected) {
            // expected
        }
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
