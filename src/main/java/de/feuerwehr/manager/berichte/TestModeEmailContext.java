package de.feuerwehr.manager.berichte;

/**
 * Request-scoped Kontext für Testmodus-E-Mail-Wahl. Wird beim Publish in
 * {@link BerichteEmailEvent} übernommen (wichtig für AFTER_COMMIT-Listener).
 */
public final class TestModeEmailContext {

    private static final ThreadLocal<State> HOLDER = new ThreadLocal<>();

    private TestModeEmailContext() {}

    public static void set(TestModeEmailDelivery delivery, String actorEmail) {
        HOLDER.set(new State(
                delivery != null ? delivery : TestModeEmailDelivery.NONE,
                blankToNull(actorEmail)));
    }

    public static boolean isSet() {
        return HOLDER.get() != null;
    }

    public static TestModeEmailDelivery getDelivery() {
        State state = HOLDER.get();
        return state != null ? state.delivery() : TestModeEmailDelivery.NONE;
    }

    public static String getActorEmail() {
        State state = HOLDER.get();
        return state != null ? state.actorEmail() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record State(TestModeEmailDelivery delivery, String actorEmail) {}
}
