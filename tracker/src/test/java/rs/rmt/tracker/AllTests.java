package rs.rmt.tracker;

import rs.rmt.tracker.registry.TrackerRegistryTest;
import rs.rmt.tracker.testutil.TestRunner;
import rs.rmt.tracker.users.SessionStoreTest;
import rs.rmt.tracker.users.UserStoreTest;
import rs.rmt.tracker.util.JsonTest;

public final class AllTests {
    public static void main(String[] args) {
        TestRunner.run(
                JsonTest.class,
                TrackerRegistryTest.class,
                TrackerApiIntegrationTest.class,
                UserStoreTest.class,
                SessionStoreTest.class,
                AuthApiIntegrationTest.class
        );
    }
}
