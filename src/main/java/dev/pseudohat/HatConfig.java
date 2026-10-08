package dev.pseudohat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the nickname-to-model mapping out of the resource packs.
 *
 * <p>Every {@code assets/pseudohat/config/*.json} is loaded and all of them apply at once, so a pack
 * author can split the mapping across as many files as they like or keep it in one. Files may also
 * come from several packs; for the same file name the highest-priority pack wins, exactly like any
 * other resource.
 *
 * <p>Nicknames are matched <em>exactly</em>, including case, so {@code GGSHNIKK} and {@code ggshnikk}
 * are two different players and can wear two different models. The case cannot live in the file name
 * instead: {@code Identifier} only accepts {@code _ - a-z 0-9 / .} in a path, so {@code GGSHNIKK.json}
 * is not a loadable resource at all. Keeping the nickname in the JSON sidesteps that limit.
 *
 * <p>Format, with either form accepted per entry:
 * <pre>{@code
 * {
 *   "hats": {
 *     "GGSHNIKK": "pseudohat:hat",
 *     "Notch": { "model": "pseudohat:crown" }
 *   }
 * }
 * }</pre>
 */
public final class HatConfig {
	/** Namespace the configs live in, i.e. {@code assets/pseudohat/config/}. */
	public static final String NAMESPACE = "pseudohat";

	private static final String DIRECTORY = "config";
	private static final String KEY = "hats";

	private static volatile Map<String, Identifier> byNickname;

	private HatConfig() {
	}

	/** Drops the cache; the next lookup reloads. Called when resource packs reload. */
	public static void invalidate() {
		byNickname = null;
	}

	/** @return the model {@code nickname} asked for, or {@code null} if no config names them. */
	@Nullable
	public static Identifier modelFor(String nickname) {
		if (nickname == null || nickname.isEmpty()) {
			return null;
		}

		Map<String, Identifier> config = byNickname;

		if (config == null) {
			config = load();
			byNickname = config;
		}

		return config.get(nickname);
	}

	private static Map<String, Identifier> load() {
		ResourceManager resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();

		// listResourceStacks walks the directory recursively, so the ids it hands back are full paths
		// like "config/hats.json". Only a direct child counts: nested files are pack layout, not
		// mappings, and reading them here would make a pack's own subfolders part of the config.
		Map<Identifier, List<Resource>> files = resources.listResourceStacks(DIRECTORY, HatConfig::isTopLevelConfig);

		// Sorted so the merged result does not depend on map iteration order; a duplicate nickname in
		// two files is a pack authoring mistake and is reported rather than silently resolved.
		Map<String, Map<String, Identifier>> perFile = new TreeMap<>();

		for (Map.Entry<Identifier, List<Resource>> entry : files.entrySet()) {
			List<Resource> stack = entry.getValue();

			if (stack.isEmpty()) {
				continue;
			}

			// getResourceStack orders by pack priority, so the last one is the winner.
			Map<String, Identifier> parsed = parse(stack.get(stack.size() - 1), entry.getKey());

			if (!parsed.isEmpty()) {
				perFile.put(entry.getKey().getPath(), parsed);
			}
		}

		Map<String, Identifier> merged = new LinkedHashMap<>();
		Map<String, String> origin = new HashMap<>();

		for (Map.Entry<String, Map<String, Identifier>> file : perFile.entrySet()) {
			for (Map.Entry<String, Identifier> mapping : file.getValue().entrySet()) {
				String previous = origin.put(mapping.getKey(), file.getKey());

				if (previous != null) {
					PseudoHat.LOGGER.warn(
							"Nickname '{}' is declared in both {} and {}; {} wins",
							mapping.getKey(), previous, file.getKey(), file.getKey()
					);
				}

				merged.put(mapping.getKey(), mapping.getValue());
			}
		}

		if (!merged.isEmpty()) {
			PseudoHat.LOGGER.info("Loaded {} hat mapping(s) from {}/{}", merged.size(), NAMESPACE, DIRECTORY);
		}

		return Map.copyOf(merged);
	}

	/** Direct children of {@code config/} only: {@code config/hats.json} yes, {@code config/sub/hats.json} no. */
	private static boolean isTopLevelConfig(Identifier id) {
		String path = id.getPath();
		return NAMESPACE.equals(id.getNamespace())
				&& path.startsWith(DIRECTORY + "/")
				&& path.indexOf('/', DIRECTORY.length() + 1) < 0
				&& path.endsWith(".json");
	}

	private static Map<String, Identifier> parse(Resource resource, Identifier configId) {
		Map<String, Identifier> mappings = new LinkedHashMap<>();

		try (Reader reader = resource.openAsReader()) {
			JsonElement root = StrictJsonParser.parse(reader);

			if (!root.isJsonObject()) {
				PseudoHat.LOGGER.warn("Config {} is not a JSON object, ignoring", configId);
				return mappings;
			}

			JsonObject hats = root.getAsJsonObject().getAsJsonObject(KEY);

			if (hats == null) {
				PseudoHat.LOGGER.warn("Config {} has no '{}' object, ignoring", configId, KEY);
				return mappings;
			}

			for (Map.Entry<String, JsonElement> entry : hats.entrySet()) {
				String nickname = entry.getKey();
				String rawModel = readModel(entry.getValue());
				Identifier model = rawModel == null ? null : Identifier.tryParse(rawModel);

				if (nickname.isEmpty()) {
					PseudoHat.LOGGER.warn("Config {} has an empty nickname, ignoring that entry", configId);
				} else if (model == null) {
					PseudoHat.LOGGER.warn(
							"Config {} maps '{}' to {}, which is not a usable model id", configId, nickname, rawModel
					);
				} else {
					mappings.put(nickname, model);
				}
			}
		} catch (IOException | RuntimeException e) {
			PseudoHat.LOGGER.error("Failed to read config {}", configId, e);
		}

		return mappings;
	}

	/**
	 * Accepts both {@code "ns:path"} and {@code {"model": "ns:path"}}.
	 *
	 * <p>Only real JSON strings count. A bare number would otherwise be stringified into something like
	 * {@code minecraft:42}, which parses as a valid id and silently bakes into nothing.
	 */
	@Nullable
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
}