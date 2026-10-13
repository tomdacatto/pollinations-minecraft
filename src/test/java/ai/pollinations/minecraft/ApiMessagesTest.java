package ai.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiMessagesTest {

    @Test
    void knownStatusesGetActionableMessages() {
        assertTrue(Api.friendlyError(402).contains("out of Pollen"));
        assertTrue(Api.friendlyError(401).contains("rejected"));
        assertTrue(Api.friendlyError(403).contains("rejected"));
        assertTrue(Api.friendlyError(429).contains("Too many requests"));
        assertTrue(Api.friendlyError(502).contains("HTTP 502"));
    }

    @Test
    void errorMessagesNeverEchoAKey() {
        for (int status : List.of(400, 401, 402, 403, 404, 429, 500, 503)) {
            assertFalse(Api.friendlyError(status).contains("Bearer"));
            assertFalse(Api.friendlyError(status).contains("sk_"));
            assertFalse(Api.friendlyError(status).contains("pk_"));
        }
    }

    @Test
    void onlyTwoHundredSeriesCountsAsSuccess() {
        assertTrue(Api.success(200));
        assertTrue(Api.success(204));
        assertFalse(Api.success(302));
        assertFalse(Api.success(402));
        assertFalse(Api.success(500));
    }

    @Test
    void settingsHideTheKeyInToString() {
        Settings settings = new Settings("pk_secret_value", "m", "i", 120, 0, 60, 12);

        assertFalse(settings.toString().contains("pk_secret_value"));
        assertTrue(settings.hasKey());
    }
}
