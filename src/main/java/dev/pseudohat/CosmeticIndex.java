package dev.pseudohat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a player nickname to the vanilla item model that draws the cosmetic.
 *
 * <p>Nothing here parses model JSON. {@code assets/<ns>/models/item/<name>.json} is an ordinary Java
 * Edition model that Blockbench writes and Minecraft's own {@code ModelBakery} bakes, so parent
 * chains, texture variables, atlas stitching, UV and element rotations all behave exactly as they do
 * in vanilla. The value in {@code assets/pseudohat/config/*.json} is the id of an
 * {@code assets/<ns>/items/<name>.json} item definition, which in turn points at the geometry model.
 *
 * <p>26.2 bakes item geometry into 0..1 units rather than the 0..16 of a block model, which is why a hat
 * is authored on Blockbench's own head reference box and placed by a constant in the layer plus the
 * model's {@code display.head} entry. See {@code PseudoHatLayer} for the render side, where the
 * {@code ItemDisplayContext.HEAD} context is what makes vanilla apply that entry.
 */
public final class CosmeticIndex {
	/** Keyed by model id, so players sharing a model share one bake and one entry. */
	private static final Map<Identifier, ItemModel> CACHE = new ConcurrentHashMap<>();

	private CosmeticIndex() {
	}

	/**
	 * Returns the baked model for {@code nickname}, or {@code null} when no config entry names it.
	 *
	 * @param modelId item model id, e.g. {@code pseudohat:hat}
	 */
	@Nullable
	public static ItemModel forPlayer(String nickname, @Nullable Identifier modelId) {
		if (nickname == null || nickname.isEmpty()) {
			return null;
		}

		return model(modelId);
	}

	/**
	 * Returns the baked model for an arbitrary item id, which is how the glow pass reaches a model
	 * that is not the one the config named.
	 *
	 * @param modelId item model id, e.g. {@code pseudohat:hat_glow}
	 */
	@Nullable
	public static ItemModel model(@Nullable Identifier modelId) {
		if (modelId == null) {
			return null;
		}

		return CACHE.computeIfAbsent(modelId, CosmeticIndex::load);
	}

	private static ItemModel load(Identifier id) {
		ModelManager models = Minecraft.getInstance().getModelManager();
		ItemModel model = models.getItemModel(id);

		if (PseudoHat.DEBUG) {
			PseudoHat.LOGGER.info("[index] {} -> {}", id, model.getClass().getSimpleName());
		}

		return model;
	}

	/** Drops baked models so the next lookup happens against freshly loaded packs. */
	public static void invalidate() {
		CACHE.clear();
	}
}