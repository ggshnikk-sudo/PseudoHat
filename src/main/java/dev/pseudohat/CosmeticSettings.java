package dev.pseudohat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-cosmetic render settings, read from {@code assets/pseudohat/config/models/*.json}.
 *
 * <p>A nickname-to-model mapping says <em>who</em> wears something; these settings say <em>how</em> it
 * draws. Any cosmetic id that has no entry here falls back to plain item settings, so a pack written
 * before this file existed keeps working untouched.
 *
 * <p>Format:
 * <pre>{@code
 * {
 *   "cosmetics": {
 *     "pseudohat:poppy": {},
 *     "pseudohat:hat": {
 *       "type": "geckolib",
 *       "geo": "pseudohat:hat",
 *       "texture": "pseudohat:textures/entity/hat.png",
 *       "animations": "pseudohat:hat",
 *       "scale": 1.0,
 *       "offset": [0, 0, 0],
 *       "rotation": [0, 0, 0],
 *       "glow": true,
 *       "transparent": true,
 *       "default": "animation.hat.idle",
 *       "events": {
 *         "onSprint": "animation.hat.run",
 *         "onHurt": { "animation": "animation.hat.hurt", "mode": "once" }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>{@code offset} is in geometry pixels (16 per block, y up) and {@code rotation} in degrees, both
 * applied after vanilla's head placement so they are pure authoring nudges: write the model centred on
 * the head in Blockbench, then move it with these numbers until it sits right.
 *
 * <p>{@code transparent} switches the base pass from GeckoLib's default cutout (alpha-test, no
 * blending) to translucent blending, for models with soft semi-transparent pixels like smoke.
 *
 * <p>{@code events} rules are evaluated in declaration order and the first matching one plays; a rule
 * without {@code "mode"} loops while its trigger holds, {@code "once"} plays a single time when the
 * trigger starts and the animation's own length decides when it ends. {@code default} loops whenever
 * nothing matches, and with neither a match nor a default the model rests in its bind pose.
 *
 * <p>All parsing lives here as a pure function over Gson so it can be tested without a running game;
 * the file IO, merging and logging belong to {@link CosmeticSettingsIndex}.
 */
public record CosmeticSettings(
		Identifier id,
		Type type,
		Identifier itemModel,
		boolean glow,
		@Nullable Identifier glowModel,
		@Nullable Identifier glowTexture,
		boolean transparent,
		Identifier geo,
		Identifier texture,
		Identifier animations,
		float scale,
		Vec3 offset,
		Vec3 rotation,
		@Nullable String defaultAnimation,
		List<EventRule> events
) {
	/** The key the settings live under. */
	public static final String KEY = "cosmetics";

	/** How the cosmetic draws. Anything that is not {@link #GECKOLIB} is a vanilla item model. */
	public enum Type {
		ITEM("item"),
		GECKOLIB("geckolib");

		private static final Map<String, Type> BY_KEY = new LinkedHashMap<>();

		static {
			for (Type type : values()) {
				BY_KEY.put(type.key, type);
			}
		}

		private final String key;

		Type(String key) {
			this.key = key;
		}

		public String key() {
			return this.key;
		}

		@Nullable
		public static Type fromKey(String key) {
			return BY_KEY.get(key);
		}
	}

	/** One translation/rotation step, in geometry pixels and degrees. */
	public record Vec3(float x, float y, float z) {
		public static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);
	}

	/** A single {@code "events"} entry: play {@code animation} while {@code event} holds. */
	public record EventRule(CosmeticEvent event, String animation, boolean once) {
	}

	/**
	 * What one settings file parsed to.
	 *
	 * @param cosmetics settings by cosmetic id, empty when the file declares nothing usable
	 * @param problems  human-readable authoring errors, logged by the index
	 * @param declared  whether the file had a {@code "cosmetics"} object at all
	 */
	public record Result(Map<String, CosmeticSettings> cosmetics, List<String> problems, boolean declared) {
	}

	public CosmeticSettings {
		events = List.copyOf(events);
	}

	/** The animation to hold when no event rule matches, or {@code null} to rest in bind pose. */
	@Nullable
	public String defaultAnimation() {
		return this.defaultAnimation;
	}

	/**
	 * Parses one settings file.
	 *
	 * <p>Never throws on bad authoring: every problem is collected into {@link Result#problems()} and
	 * the remaining entries still load. Only a structurally broken file (not an object, or no
	 * {@code "cosmetics"} key) reports {@code declared = false}.
	 */
	public static Result parse(Identifier fileId, JsonElement root) {
		List<String> problems = new ArrayList<>();
		Map<String, CosmeticSettings> cosmetics = new LinkedHashMap<>();

		if (!root.isJsonObject()) {
			problems.add(fileId + " is not a JSON object");
			return new Result(cosmetics, problems, false);
		}

		JsonElement declared = root.getAsJsonObject().get(KEY);

		if (declared == null || !declared.isJsonObject()) {
			problems.add(fileId + " has no '" + KEY + "' object");
			return new Result(cosmetics, problems, false);
		}

		for (Map.Entry<String, JsonElement> entry : declared.getAsJsonObject().entrySet()) {
			String rawId = entry.getKey();
			Identifier id = Identifier.tryParse(rawId);

			if (id == null) {
				problems.add(rawId + ": not a usable cosmetic id, entry skipped");
				continue;
			}

			if (!entry.getValue().isJsonObject()) {
				problems.add(rawId + ": entry must be an object, entry skipped");
				continue;
			}

			CosmeticSettings settings = parseEntry(id, entry.getValue().getAsJsonObject(), problems);

			if (settings != null) {
				cosmetics.put(rawId, settings);
			}
		}

		return new Result(cosmetics, problems, true);
	}

	@Nullable
	private static CosmeticSettings parseEntry(Identifier id, JsonObject object, List<String> problems) {
		String rawType = readString(object, "type");
		Type type = rawType == null ? Type.ITEM : Type.fromKey(rawType);

		if (type == null) {
			problems.add(id + ": unknown type '" + rawType + "', expected 'item' or 'geckolib', entry skipped");
			return null;
		}

		boolean geckolib = type == Type.GECKOLIB;
		Identifier geo = geckolib ? readId(object, "geo", id, problems) : id;
		Identifier texture = geckolib ? readTexture(object, geo, problems) : null;
		Identifier animations = geckolib ? readId(object, "animations", geo, problems) : null;
		Identifier itemModel = geckolib ? id : readId(object, "model", id, problems);

		float scale = readFloat(object, "scale", 1.0F, problems);
		Vec3 offset = readVec3(object, "offset", problems);
		Vec3 rotation = readVec3(object, "rotation", problems);

		boolean glow;
		Identifier glowModel = null;
		Identifier glowTexture = null;

		if (object.has("glow")) {
			JsonElement glowElement = object.get("glow");

			if (glowElement.isJsonPrimitive() && glowElement.getAsJsonPrimitive().isBoolean()) {
				glow = glowElement.getAsBoolean();
			} else if (glowElement.isJsonObject()) {
				glow = true;
				JsonObject glowObject = glowElement.getAsJsonObject();
				glowModel = geckolib ? null : readOptionalId(glowObject, "model");
				glowTexture = geckolib ? readOptionalId(glowObject, "texture") : null;

				if (!geckolib && glowModel == null) {
					glowModel = itemModel;
				}
			} else {
				glow = false;
				problems.add(id + ": 'glow' must be true, false or an object, glow disabled");
			}
		} else {
			glow = false;
		}

		String defaultAnimation = readString(object, "default");
		List<EventRule> events = readEvents(id, object, problems);
		boolean transparent = readBoolean(object, "transparent", problems);

		return new CosmeticSettings(
				id, type, itemModel, glow, glowModel, glowTexture, transparent, geo, texture, animations,
				scale, offset, rotation, defaultAnimation, events
		);
	}

	private static List<EventRule> readEvents(Identifier id, JsonObject object, List<String> problems) {
		List<EventRule> rules = new ArrayList<>();

		if (!object.has("events")) {
			return rules;
		}

		JsonElement eventsElement = object.get("events");

		if (!eventsElement.isJsonObject()) {
			problems.add(id + ": 'events' must be an object, no rules loaded");
			return rules;
		}

		// Gson's JsonObject keeps insertion order, so rule priority is file order.
		for (Map.Entry<String, JsonElement> entry : eventsElement.getAsJsonObject().entrySet()) {
			CosmeticEvent event = CosmeticEvent.fromKey(entry.getKey());

			if (event == null) {
				problems.add(id + ": unknown event '" + entry.getKey() + "', rule skipped");
				continue;
			}

			String animation;
			boolean once = false;
			JsonElement value = entry.getValue();

			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				animation = value.getAsString();
			} else if (value.isJsonObject()) {
				animation = readString(value.getAsJsonObject(), "animation");
				once = "once".equalsIgnoreCase(readString(value.getAsJsonObject(), "mode"));

				if (animation == null) {
					problems.add(id + ": event '" + entry.getKey() + "' has no 'animation', rule skipped");
					continue;
				}
			} else {
				problems.add(id + ": event '" + entry.getKey() + "' must be a string or an object, rule skipped");
				continue;
			}

			rules.add(new EventRule(event, animation, once));
		}

		return rules;
	}

	/** Reads a string property, recording a problem when it is present but not a string. */
	/** Reads a boolean property, recording a problem when it is present but not a boolean. */
	private static boolean readBoolean(JsonObject object, String key, List<String> problems) {
		JsonElement element = object.get(key);

		if (element == null) {
			return false;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			problems.add("'" + key + "' must be true or false, using false");
			return false;
		}

		return element.getAsBoolean();
	}

	@Nullable
	private static String readString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
				? element.getAsString()
				: null;
	}

	@Nullable
	private static Identifier readOptionalId(JsonObject object, String key) {
		String raw = readString(object, key);
		return raw == null ? null : Identifier.tryParse(raw);
	}

	/** Reads an id property with a fallback, complaining only when the file asked for a bad one. */
	private static Identifier readId(JsonObject object, String key, Identifier fallback, List<String> problems) {
		String raw = readString(object, key);

		if (raw == null) {
			return fallback;
		}

		Identifier id = Identifier.tryParse(raw);

		if (id == null) {
			problems.add("'" + key + "' value '" + raw + "' is not a usable id, using " + fallback);
			return fallback;
		}

		return id;
	}

	/** Texture defaults to beside the geometry: {@code ns:path} becomes {@code ns:textures/entity/path.png}. */
	public static Identifier textureBeside(Identifier geo) {
		return Identifier.fromNamespaceAndPath(geo.getNamespace(), "textures/entity/" + geo.getPath() + ".png");
	}

	/** Texture defaults to beside the geometry: {@code ns:path} becomes {@code ns:textures/entity/path.png}. */
	private static Identifier readTexture(JsonObject object, Identifier geo, List<String> problems) {
		String raw = readString(object, "texture");

		if (raw == null) {
			return textureBeside(geo);
		}

		Identifier texture = Identifier.tryParse(raw);

		if (texture == null) {
			problems.add("'texture' value '" + raw + "' is not a usable id, using " + geo);
			return textureBeside(geo);
		}

		return texture;
	}

	private static float readFloat(JsonObject object, String key, float fallback, List<String> problems) {
		JsonElement element = object.get(key);

		if (element == null) {
			return fallback;
		}

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			problems.add("'" + key + "' must be a number, using " + fallback);
			return fallback;
		}

		float value = element.getAsFloat();

		if (!Float.isFinite(value) || value <= 0.0F) {
			problems.add("'" + key + "' must be a positive finite number, using " + fallback);
			return fallback;
		}

		return value;
	}

	private static Vec3 readVec3(JsonObject object, String key, List<String> problems) {
		JsonElement element = object.get(key);

		if (element == null) {
			return Vec3.ZERO;
		}

		if (!element.isJsonArray()) {
			problems.add("'" + key + "' must be an array of three numbers, using [0, 0, 0]");
			return Vec3.ZERO;
		}

		JsonArray array = element.getAsJsonArray();

		if (array.size() != 3) {
			problems.add("'" + key + "' must have exactly three numbers, using [0, 0, 0]");
			return Vec3.ZERO;
		}

		float[] values = new float[3];

		for (int i = 0; i < 3; i++) {
			JsonElement component = array.get(i);

			if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber() || !Float.isFinite(component.getAsFloat())) {
				problems.add("'" + key + "' must be an array of three finite numbers, using [0, 0, 0]");
				return Vec3.ZERO;
			}

			values[i] = component.getAsFloat();
		}

		return new Vec3(values[0], values[1], values[2]);
	}
}
