package dev.pseudohat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.StrictJsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The nickname-to-model mapping is case sensitive on purpose, so these tests pin that behaviour down
 * without needing a running game. The parsing helpers are exercised through a stand-in that mirrors
 * what {@link HatConfig} accepts.
 */
class HatConfigMappingTest {
	/** Mirrors HatConfig#readModel: both {@code "ns:path"} and {@code {"model": "ns:path"}}. */
	private static String readModel(JsonElement value) {
		if (value.isJsonPrimitive()) {
			return value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
		}

		if (value.isJsonObject()) {
			JsonElement model = value.getAsJsonObject().get("model");
			return model != null && model.isJsonPrimitive() && model.getAsJsonPrimitive().isString()
					? model.getAsString()
					: null;
		}

		return null;
	}

	private static Map<String, String> parse(String json) {
		Map<String, String> mappings = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> entry :
				StrictJsonParser.parse(json).getAsJsonObject().getAsJsonObject("hats").entrySet()) {
			String model = readModel(entry.getValue());

			if (!entry.getKey().isEmpty() && model != null) {
				mappings.put(entry.getKey(), model);
			}
		}

		return mappings;
	}

	@Test
	@DisplayName("nicknames differing only in case are two different players")
	void caseIsSignificant() {
		Map<String, String> mappings = parse(
				"{\"hats\":{\"GGSHNIKK\":\"pseudohat:block/upper\",\"ggshnikk\":\"pseudohat:block/lower\"}}"
		);

		assertEquals(2, mappings.size());
		assertEquals("pseudohat:block/upper", mappings.get("GGSHNIKK"));
		assertEquals("pseudohat:block/lower", mappings.get("ggshnikk"));
		assertNotEquals(mappings.get("GGSHNIKK"), mappings.get("ggshnikk"));
	}

	@Test
	@DisplayName("a lookup does not fall back to a different case")
	void lookupIsExact() {
		Map<String, String> mappings = parse("{\"hats\":{\"Notch\":\"pseudohat:block/crown\"}}");

		assertEquals("pseudohat:block/crown", mappings.get("Notch"));
		assertNull(mappings.get("notch"));
		assertNull(mappings.get("NOTCH"));
	}

	@Test
	@DisplayName("a nickname may point at a shared model")
	void nicknamesMayShareOneModel() {
		Map<String, String> mappings = parse(
				"{\"hats\":{\"Notch\":\"pseudohat:block/crown\",\"Steve\":\"pseudohat:block/crown\"}}"
		);

		assertEquals(2, mappings.size());
		assertEquals(mappings.get("Notch"), mappings.get("Steve"));
	}

	@Test
	@DisplayName("both the bare and the object form parse to the same model id")
	void objectFormIsAccepted() {
		Map<String, String> mappings = parse(
				"{\"hats\":{\"Notch\":\"pseudohat:block/crown\",\"Steve\":{\"model\":\"pseudohat:block/crown\"}}}"
		);

		assertEquals("pseudohat:block/crown", mappings.get("Notch"));
		assertEquals("pseudohat:block/crown", mappings.get("Steve"));
	}

	@Test
	@DisplayName("entries with an empty nickname or an unusable model are dropped")
	void invalidEntriesAreDropped() {
		Map<String, String> mappings = parse(
				"{\"hats\":{\"\":{\"model\":\"pseudohat:block/hat\"},\"Broken\":42,\"Good\":\"pseudohat:block/hat\"}}"
		);

		assertEquals(1, mappings.size());
		assertEquals("pseudohat:block/hat", mappings.get("Good"));
	}

	@Test
	@DisplayName("nicknames keep characters that a resource path could never hold")
	void nicknameIsNotConfinedToPathCharacters() {
		// Identifier only accepts _ - a-z 0-9 / ., so a filename could not carry this nickname.
		Map<String, String> mappings = parse("{\"hats\":{\"GGSHNIKK\":\"pseudohat:block/hat\"}}");

		assertEquals("pseudohat:block/hat", mappings.get("GGSHNIKK"));
	}
}