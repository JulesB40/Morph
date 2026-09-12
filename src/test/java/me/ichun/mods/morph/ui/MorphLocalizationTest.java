package me.ichun.mods.morph.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphLocalizationTest {
    @Test void englishAndFrenchShipTheSameKeysAndFormatArguments() throws Exception {
        var english = language("en_us");
        var french = language("fr_fr");
        assertEquals(english.keySet(), french.keySet());
        Pattern placeholder = Pattern.compile("%(?:\\d+\\$)?[a-z]");
        for (String key : english.keySet()) {
            var expected = placeholder.matcher(english.get(key).getAsString()).results().map(match -> match.group()).toList();
            var actual = placeholder.matcher(french.get(key).getAsString()).results().map(match -> match.group()).toList();
            assertEquals(expected, actual, key);
        }
    }
    private static JsonObject language(String locale) throws Exception {
        try (var input = MorphLocalizationTest.class.getResourceAsStream("/assets/morph/lang/" + locale + ".json")) {
            assertNotNull(input, locale);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
