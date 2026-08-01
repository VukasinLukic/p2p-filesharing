package rs.rmt.tracker.testutil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reflection-based runner: invokes every no-arg method named "testXxx" on each given class. */
public final class TestRunner {
    private TestRunner() {}

    public static void run(Class<?>... classes) {
        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        for (Class<?> clazz : classes) {
            Object instance;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.out.println("[SETUP-FAIL] " + clazz.getName() + ": " + e);
                failed++;
                continue;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0) continue;
                m.setAccessible(true);
                String label = clazz.getSimpleName() + "." + m.getName();
                try {
                    m.invoke(instance);
                    System.out.println("[PASS] " + label);
                    passed++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("[FAIL] " + label + " - " + cause);
                    failures.add(label + ": " + cause);
                    failed++;
                }
            }
        }

        System.out.println();
        System.out.println("=== " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.out.println("Failures:");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
    }
}
