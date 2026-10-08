package dev.pseudohat;

import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads the per-cosmetic render settings from {@code assets/pseudohat/config/models/*.json}.
 *
 * <p>Same rules as {@link HatConfig}: every file applies, several packs merge, and for one file name
 * the highest-priority pack wins. Files nested deeper than the directory are ignored, so a pack may
 * keep its own layout beside them without being parsed as settings by accident.
 *
 * <p>A cosmetic id with no entry here is not an error: {@link #forId} hands back plain item settings
 * identical to the mod's behaviour before this file existed, which is what keeps old packs working.
 */
public final class CosmeticSettingsIndex {
	private static volatile Map<Identifier, CosmeticSettings> byId;

	private CosmeticSettingsIndex() {
	}

	/** Drops the cache; the next lookup reloads. Called when resource packs reload. */
	public static void invalidate() {
		byId = null;
	}

	/**
	 * Returns the settings for {@code cosmeticId}, falling back to item defaults when no file declares it.
	 */
	public static CosmeticSettings forId(Identifier cosmeticId) {
		if (cosmeticId == null) {
			return null;
		}

		Map<Identifier, CosmeticSettings> settings = byId;

		if (settings == null) {
			settings = load();
			byId = settings;
		}

		CosmeticSettings declared = settings.get(cosmeticId);

		return declared != null ? declared : defaultsFor(cosmeticId);
	}

	/** Settings a cosmetic gets when no file mentions it: an item model under its own id. */
	public static CosmeticSettings defaultsFor(Identifier cosmeticId) {
		return new CosmeticSettings(
				cosmeticId,
				CosmeticSettings.Type.ITEM,
				cosmeticId,
				false,
				null,
				null,
				false,
				cosmeticId,
				CosmeticSettings.textureBeside(cosmeticId),
				cosmeticId,
				1.0F,
				CosmeticSettings.Vec3.ZERO,
				CosmeticSettings.Vec3.ZERO,
				null,
				List.of()
		);
	}

	private static Map<Identifier, CosmeticSettings> load() {
		ResourceManager resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();

		// listResourceStacks walks the directory recursively, so the id is the full path:
		// "config/models/hat.json". Only a direct child counts, anything deeper is not ours to read.
		Map<Identifier, List<Resource>> files = resources.listResourceStacks("config/models", CosmeticSettingsIndex::isDirectChild);

		// Sorted so the merged result does not depend on map iteration order; the same cosmetic id in
		// two files is a pack authoring mistake and is reported rather than silently resolved.
		Map<Identifier, Map<Identifier, CosmeticSettings>> perFile = new TreeMap<>();

		for (Map.Entry<Identifier, List<Resource>> entry : files.entrySet()) {
			List<Resource> stack = entry.getValue();

			if (stack.isEmpty()) {
				continue;
			}

			// getResourceStack orders by pack priority, so the last one is the winner.
			CosmeticSettings.Result parsed = parse(stack.get(stack.size() - 1), entry.getKey());

			for (String problem : parsed.problems()) {
				PseudoHat.LOGGER.warn("{}: {}", entry.getKey(), problem);
			}

			if (!parsed.cosmetics().isEmpty()) {
				Map<Identifier, CosmeticSettings> byFileId = new HashMap<>();

				for (Map.Entry<String, CosmeticSettings> setting : parsed.cosmetics().entrySet()) {
					byFileId.put(setting.getValue().id(), setting.getValue());
				}

				perFile.put(entry.getKey(), byFileId);
			}
		}

		Map<Identifier, CosmeticSettings> merged = new HashMap<>();
		Map<Identifier, Identifier> origin = new HashMap<>();

		for (Map.Entry<Identifier, Map<Identifier, CosmeticSettings>> file : perFile.entrySet()) {
			for (Map.Entry<Identifier, CosmeticSettings> setting : file.getValue().entrySet()) {
				Identifier previous = origin.put(setting.getKey(), file.getKey());

				if (previous != null) {
					PseudoHat.LOGGER.warn(
							"Cosmetic '{}' is declared in both {} and {}; {} wins",
							setting.getKey(), previous, file.getKey(), file.getKey()
					);
				}

				merged.put(setting.getKey(), setting.getValue());
			}
		}

		if (!merged.isEmpty()) {
			PseudoHat.LOGGER.info(
					"Loaded {} cosmetic setting(s) from {}/config/models", merged.size(), HatConfig.NAMESPACE
			);
		}

		return Map.copyOf(merged);
	}

	private static boolean isDirectChild(Identifier id) {
		String path = id.getPath();
		int start = "config/models/".length();
		return path.startsWith("config/models/") && path.indexOf('/', start) < 0 && path.endsWith(".json");
	}

	private static CosmeticSettings.Result parse(Resource resource, Identifier configId) {
		try (Reader reader = resource.openAsReader()) {
			JsonElement root = StrictJsonParser.parse(reader);
			return CosmeticSettings.parse(configId, root);
		} catch (IOException | RuntimeException e) {
			PseudoHat.LOGGER.error("Failed to read settings {}", configId, e);
			return new CosmeticSettings.Result(Map.of(), List.of(), false);
		}
	}
}
