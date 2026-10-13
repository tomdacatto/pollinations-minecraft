package ai.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatCodecTest {

    @Test
    void bodyPutsSystemPersonaThenHistoryThenUserMessage() {
        List<Turn> history = List.of(new Turn("user", "hi"), new Turn("assistant", "hello there"));

        String body = ChatCodec.buildBody(
                "amazon/nova-micro-v1", 120, "Ada", "a blacksmith's apprentice", history, "What do you make?");

        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("amazon/nova-micro-v1", json.get("model").getAsString());
        assertEquals(120, json.get("max_tokens").getAsInt());
        JsonArray messages = json.getAsJsonArray("messages");
        assertEquals(4, messages.size());
        assertEquals("system", role(messages, 0));
        assertTrue(content(messages, 0).contains("Ada"));
        assertTrue(content(messages, 0).contains("blacksmith's apprentice"));
        assertEquals("user", role(messages, 1));
        assertEquals("hi", content(messages, 1));
        assertEquals("assistant", role(messages, 2));
        assertEquals("What do you make?", content(messages, 3));
        assertEquals("user", role(messages, 3));
    }

    @Test
    void userMessageIsFlattenedAndCapped() {
        String body = ChatCodec.buildBody("m", 16, "Ada", "p", List.of(), "  line one\n\tline two  ");
        JsonArray messages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
        assertEquals("line one line two", content(messages, 1));

        String longBody = ChatCodec.buildBody("m", 16, "Ada", "p", List.of(), "x".repeat(900));
        JsonArray longMessages = JsonParser.parseString(longBody).getAsJsonObject().getAsJsonArray("messages");
        assertTrue(content(longMessages, 1).length() <= ChatCodec.USER_MESSAGE_MAX);
    }

    @Test
    void parsesFirstChoiceAndFlattensNewlines() {
        String reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi!\\nNice to meet you.\"}}],"
                + "\"usage\":{\"total_tokens\":12}}";

        assertEquals(Optional.of("Hi! Nice to meet you."), ChatCodec.parseReply(reply));
    }

    @Test
    void rejectsEmptyAndMalformedReplies() {
        assertEquals(Optional.empty(), ChatCodec.parseReply("{\"choices\":[]}"));
        assertEquals(Optional.empty(), ChatCodec.parseReply("{\"choices\":[{\"message\":{\"content\":null}}]}"));
        assertEquals(Optional.empty(), ChatCodec.parseReply("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}"));
        assertEquals(Optional.empty(), ChatCodec.parseReply("<html>502 Bad Gateway</html>"));
        assertEquals(Optional.empty(), ChatCodec.parseReply("{\"error\":\"nope\"}"));
    }

    @Test
    void longRepliesAreTruncatedForChat() {
        String huge = "{\"choices\":[{\"message\":{\"content\":\"" + "word ".repeat(200) + "\"}}]}";

        String reply = ChatCodec.parseReply(huge).orElseThrow();

        assertTrue(reply.length() <= ChatCodec.REPLY_MAX);
        assertTrue(reply.endsWith("..."));
        assertFalse(reply.contains("\n"));
    }

    private static String role(JsonArray messages, int index) {
        return messages.get(index).getAsJsonObject().get("role").getAsString();
    }

    private static String content(JsonArray messages, int index) {
        return messages.get(index).getAsJsonObject().get("content").getAsString();
    }
}
