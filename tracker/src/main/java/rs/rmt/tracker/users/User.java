package rs.rmt.tracker.users;

import rs.rmt.tracker.util.Json;

import java.util.Map;

/**
 * A registered account. The credential fields never leave the tracker process - everything the
 * API returns goes through {@link #toPublicJson()}.
 */
public record User(String userId, String username, String displayName,
                   String passwordSalt, String passwordHash, long createdAtMillis) {

    /** Safe to send to a client: no salt, no hash. */
    public Map<String, Object> toPublicJson() {
        return Json.obj(
                "userId", userId,
                "username", username,
                "displayName", displayName,
                "createdAt", createdAtMillis);
    }

    Map<String, Object> toStorageJson() {
        return Json.obj(
                "userId", userId,
                "username", username,
                "displayName", displayName,
                "passwordSalt", passwordSalt,
                "passwordHash", passwordHash,
                "createdAt", createdAtMillis);
    }

    static User fromStorageJson(Map<String, Object> json) {
        return new User(
                Json.getString(json, "userId"),
                Json.getString(json, "username"),
                Json.getString(json, "displayName"),
                Json.getString(json, "passwordSalt"),
                Json.getString(json, "passwordHash"),
                Json.getLong(json, "createdAt", 0));
    }
}
