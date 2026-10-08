package dev.pseudohat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.StrictJsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code display.head} block of the item-model json Blockbench writes next to a geckolib
 * geometry. A gecko item's placement is not baked into the {@code .geo.json} file; the GeckoLib
 * plugin exports it separately as an item model whose only payload is the display transform, so a
 * cosmetic that was tuned in Blockbench's item tab carries its positioning in that file.
 *
 * <p>Only a display-only model qualifies: Blockbench's export has {@code "parent": "builtin/entity"}
 * and no {@code elements}. A real element model in the same slot (the sample pack's
 * {@code hat.json} is an older vanilla model) keeps rendering through {@link PseudoHatLayer}'s config
 * transforms instead, so adding a display file never silently repositions a model that has its own
 * geometry.
 *
 * <p>The transform is read with vanilla's semantics: {@code translation} is in geometry pixels (one
 * sixteenth of a block, matching {@code CosmeticSettings.offset}), {@code rotation} in degrees about
 * the head, and {@code scale} a plain multiplier, applied in that order by
 * {@link PseudoHatLayer#applyAuthoringTransforms}.
 */
public final class HeadDisplay {
	private static final Map<Identifier, Transform> CACHE = new ConcurrentHashMap<>();
	private static final String DISPLAY = "display";
	private static final String HEAD = "head";

	/** A parsed {@code display.head}, or {@link #NONE} when the cosmetic has no display file. */
	public record Transform(CosmeticSettings.Vec3 offset, CosmeticSettings.Vec3 rotation, float scale) {
		public static final Transform NONE = new Transform(CosmeticSettings.Vec3.ZERO, CosmeticSettings.Vec3.ZERO, 1.0F);

		public boolean isNone() {
			return this == NONE;
		}
	}

	private HeadDisplay() {
	}

	/**
	 * The display transform for {@code cosmeticId}, or {@link Transform#NONE} when the id has no
	 * display-only item model. Cached; drop with {@link #invalidate()} on resource reload.
	 */
	public static Transform forCosmetic(Identifier cosmeticId) {
		if (cosmeticId == null) {
			return Transform.NONE;
		}

		return CACHE.computeIfAbsent(cosmeticId, HeadDisplay::load);
	}

	/** Parses a {@code display.head} transform out of an item model json, or {@code null} when it has none. */
	@Nullable
	public static Transform parse(JsonElement root) {
		if (root == null || !root.isJsonObject()) {
			return null;
		}

		JsonObject object = root.getAsJsonObject();

		// Real geometry in the slot means this is an old-style item model, not a gecko display file.
		if (object.has("elements")) {
			return null;
		}

		JsonElement displayElement = object.get(DISPLAY);

		if (displayElement == null || !displayElement.isJsonObject()) {
			return null;
		}

		JsonElement headElement = displayElement.getAsJsonObject().get(HEAD);

		if (headElement == null || !headElement.isJsonObject()) {
			return null;
		}

		JsonObject head = headElement.getAsJsonObject();
		CosmeticSettings.Vec3 offset = vec3(head, "translation", CosmeticSettings.Vec3.ZERO);
		CosmeticSettings.Vec3 rotation = vec3(head, "rotation", CosmeticSettings.Vec3.ZERO);
		float scale = 1.0F;

		JsonElement scaleElement = head.get("scale");

		if (scaleElement != null && scaleElement.isJsonArray()) {
			JsonElement x = scaleElement.getAsJsonArray().get(0);

			if (x != null && x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber() && Float.isFinite(x.getAsFloat())) {
				scale = x.getAsFloat();
			}
		}

		return new Transform(offset, rotation, scale);
	}

	private static CosmeticSettings.Vec3 vec3(JsonObject object, String key, CosmeticSettings.Vec3 fallback) {
		JsonElement element = object.get(key);

		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
			return fallback;
		}

		float[] values = new float[3];

		for (int i = 0; i < 3; i++) {
			JsonElement component = element.getAsJsonArray().get(i);

			if (component == null || !component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()
					|| !Float.isFinite(component.getAsFloat())) {
				return fallback;
			}

			values[i] = component.getAsFloat();
		}

		return new CosmeticSettings.Vec3(values[0], values[1], values[2]);
	}

	private static Transform load(Identifier cosmeticId) {
		// The item-model slot for the cosmetic: assets/<ns>/models/item/<path>.json <-> ns:item/<path>.
		Identifier modelId = Identifier.fromNamespaceAndPath(
				cosmeticId.getNamespace(), "models/item/" + cosmeticId.getPath() + ".json"
		);

		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(modelId);

			if (resource.isEmpty()) {
				return Transform.NONE;
			}

			try (Reader reader = resource.get().openAsReader()) {
				Transform transform = parse(StrictJsonParser.parse(reader));

				if (PseudoHat.DEBUG && transform != null && !transform.isNone()) {
					PseudoHat.LOGGER.info("[display] {} -> offset={} rotation={} scale={}",
							cosmeticId, transform.offset(), transform.rotation(), transform.scale());
				}

				return transform != null ? transform : Transform.NONE;
			}
		} catch (IOException | RuntimeException e) {
			PseudoHat.LOGGER.warn("Failed to read display model {} for {}", modelId, cosmeticId, e);
			return Transform.NONE;
		}
	}

	/** Drops cached transforms so the next lookup reads freshly loaded packs. */
	public static void invalidate() {
		CACHE.clear();
	}
}