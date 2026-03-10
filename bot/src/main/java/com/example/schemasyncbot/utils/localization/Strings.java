package com.example.schemasyncbot.utils.localization;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Localized string resources for the SchemaSync bot.
 * Currently supports English. Extend the static block for other locales.
 */
public class Strings {
    private static final Map<String, Map<String, String>> STRINGS = new LinkedHashMap<>();

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("/start", "Initialize a new bot session");
        en.put("/pipelines", "Browse & select Jenkins pipelines");
        en.put("/diff", "Generate schema diff from configured parameters");
        en.put("/approve", "Apply the reviewed schema changes");
        en.put("/logs", "View Jenkins build logs for the selected pipeline");
        en.put("/status", "Show current session state & parameters");
        en.put("/help", "Detailed usage guide with examples");
        en.put("/delete <key>", "Remove a parameter from the session");
        en.put("/cancel", "Cancel current operation & reset session");
        en.put("/commands", "List all available commands");
        STRINGS.put("en", en);
    }

    public static String get(String key, Locale locale) {
        String lang = locale != null ? locale.getLanguage() : "en";
        Map<String, String> map = STRINGS.getOrDefault(lang, STRINGS.get("en"));
        return map.getOrDefault(key, key);
    }

    public static Map<String, String> getCommandDescriptions(Locale locale) {
        String lang = locale != null ? locale.getLanguage() : "en";
        return STRINGS.getOrDefault(lang, STRINGS.get("en"));
    }
} 