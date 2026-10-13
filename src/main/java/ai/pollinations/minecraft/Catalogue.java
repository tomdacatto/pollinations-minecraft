package ai.pollinations.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Checks a configured model against the public model list (no key needed). */
final class Catalogue {

    private Catalogue() {}

    /** Returns warnings for the admin log. Empty when the model is listed, not paid-only and not down. */
    static List<String> warnings(String listJson, String model, String kind) {
        List<String> warnings = new ArrayList<>();
        JsonObject entry;
        try {
            entry = findEntry(listJson, model);
        } catch (RuntimeException unreadable) {
            warnings.add("Could not read the live " + kind + " model list to check " + model + ".");
            return warnings;
        }
        if (entry == null) {
            warnings.add(kind + " model " + model + " is not in the live list. Check its id at the model list URL.");
            return warnings;
        }
        if (flag(entry, "paid_only")) {
            warnings.add(kind + " model " + model + " is paid-only; accounts without a paid balance will get errors.");
        }
        if (entry.has("health") && entry.get("health").isJsonObject()) {
            JsonElement status = entry.getAsJsonObject("health").get("status");
            if (status != null && status.isJsonPrimitive() && "down".equals(status.getAsString())) {
                warnings.add(kind + " model " + model + " is currently reporting down.");
            }
        }
        return warnings;
    }

    private static JsonObject findEntry(String listJson, String model) {
        for (JsonElement element : JsonParser.parseString(listJson).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (matches(entry, model)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean matches(JsonObject entry, String model) {
        if (entry.has("name") && entry.get("name").getAsString().equalsIgnoreCase(model)) {
            return true;
        }
        if (entry.has("aliases") && entry.get("aliases").isJsonArray()) {
            for (JsonElement alias : entry.getAsJsonArray("aliases")) {
                if (alias.isJsonPrimitive() && alias.getAsString().equalsIgnoreCase(model)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean flag(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
