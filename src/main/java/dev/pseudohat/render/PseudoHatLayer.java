package dev.pseudohat.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.pseudohat.CosmeticIndex;
import dev.pseudohat.CosmeticSettings;
import dev.pseudohat.CosmeticSettingsIndex;
import dev.pseudohat.HatConfig;
import dev.pseudohat.HeadDisplay;
import dev.pseudohat.PseudoHat;
import dev.pseudohat.geckolib.GeckoLibBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the cosmetic model on a humanoid's head while the head slot is empty, so a real helmet still
 * wins by occupying the slot.
 *
 * <p>Vanilla's head placement is reused verbatim: the pose chain below is
 * {@code CustomHeadLayer.submit}'s item branch, so a cosmetic lands on the head by construction and
 * any mismatch with a vanilla item is a vanilla bug rather than a private convention.
 *
 * <p>What draws inside that pose is decided by {@link CosmeticSettings}, read from
 * {@code assets/pseudohat/config/models/*.json}:
 *
 * <ul>
 *   <li>{@code item} (the default) keeps the original path: an ordinary item model handed to vanilla
 *       through {@link ItemModel#update} and {@link ItemStackRenderState#submit}, placed by the model's
 *       own {@code display.head} entry. A glow flag adds a second pass of that model at full bright.</li>
 *   <li>{@code geckolib} hands the frame to {@link GeckoLibBridge}, which draws an animated geometry
 *       model in the same pose. The bridge is a no-op when GeckoLib is not installed, so the mod never
 *       requires it.</li>
 * </ul>
 */
public final class PseudoHatLayer<S extends HumanoidRenderState, M extends HumanoidModel<? super S>>
		extends RenderLayer<S, M> {
	private static final ItemStack NO_ITEM = ItemStack.EMPTY;

	/**
	 * Vanilla's own head placement, reused verbatim.
	 *
	 * <p>{@code CustomHeadLayer.submit} is the only vanilla code that puts an item model on a head, and
	 * its pose chain is {@code scale(horizontalScale)} then the model's root, then {@code translateToHead()},
	 * then {@code CustomHeadLayer.translateToHead}, which applies {@code translate(0, -0.25 + yOffset, 0)},
	 * {@code rotateY(180)} and {@code scale(0.625, -0.625, -0.625)}. This layer calls those same pieces
	 * instead of rederiving the matrix.
	 *
	 * <p>Note the negative y and z in that scale: vanilla mirrors the model front to back for head display.
	 * It is part of the item-on-head contract, so models must account for it.
	 */
	private static final CustomHeadLayer.Transforms HEAD_TRANSFORMS = CustomHeadLayer.Transforms.DEFAULT;

	/**
	 * Geometry is authored in pixels, but GeckoLib converts them to blocks while baking (16 px per
	 * block: {@code JsonUtil.modelToWorldUnits} divides by 16, and bone pivots are divided the same
	 * way), so a baked model is already in block units and the renderer adds no scale of its own.
	 *
	 * <p>An 8 px wide model is therefore 0.5 blocks in model space, and the head frame scales it by
	 * 0.625, so 1.6 puts a head-sized model back at 0.5 blocks: 8 px = 0.5 &times; 1.6 &times; 0.625.
	 */
	private static final float GEO_SCALE = 1.6F;

	private ItemModelResolver resolver;

	public PseudoHatLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float a, float b) {
		if (state.isInvisibleToPlayer || !isHeadEmpty(state) || !(state instanceof HasCosmeticState worn)) {
			return;
		}

		String name = worn.pseudohat$name();
		Identifier cosmeticId = HatConfig.modelFor(name);

		if (cosmeticId == null) {
			return;
		}

		CosmeticSettings settings = CosmeticSettingsIndex.forId(cosmeticId);
		HumanoidModel<?> humanoid = this.getParentModel();

		poseStack.pushPose();

		// CustomHeadLayer.submit, item branch, unchanged.
		poseStack.scale(HEAD_TRANSFORMS.horizontalScale(), 1.0f, HEAD_TRANSFORMS.horizontalScale());
		humanoid.root().translateAndRotate(poseStack);
		humanoid.translateToHead(poseStack);
		CustomHeadLayer.translateToHead(poseStack, HEAD_TRANSFORMS);

		if (settings.type() == CosmeticSettings.Type.GECKOLIB) {
			// Geometry draws from here on; with GeckoLib missing the bridge logs once and draws nothing.
			HeadDisplay.Transform display = HeadDisplay.forCosmetic(settings.id());

			// A display file is tuned by Blockbench in vanilla item semantics: the baked model is in
			// blocks, its display.head transform already carries the intended scale, and the 0.625 head
			// frame below is part of that tuning. Applying GEO_SCALE on top would make the model as
			// wide as the display said it should be, only 1.6x too big. GEO_SCALE only compensates for
			// models without a display file, which are authored 1:1 in geometry pixels.
			if (display == null || display.isNone()) {
				poseStack.scale(GEO_SCALE, GEO_SCALE, GEO_SCALE);
			}

			applyAuthoringTransforms(poseStack, settings, display);
			GeckoLibBridge.render(new GeckoLibBridge.Request(
					name, settings, worn.pseudohat$context(), poseStack, collector, light, RenderCamera.get()
			));
		} else {
			this.submitItem(name, settings, poseStack, collector, light);
		}

		poseStack.popPose();
	}

	/**
	 * The file's own nudges, applied after vanilla's placement: offset in geometry pixels, then a
	 * rotation about the head in degrees (x, then y, then z), then a size multiplier. They are the
	 * only controls an author needs to seat a model that was drawn anywhere on the Blockbench grid.
	 *
	 * <p>The transform comes from the item-model's {@code display.head} when the cosmetic has a
	 * display-only model file (a Blockbench gecko dump), falling back to the config's own
	 * {@code offset}/{@code rotation}/{@code scale} — the display is the newer, file-authoring control,
	 * the config the older one, and the two are equivalent in units.
	 *
	 * <p>The offset is divided by 16 because a geometry pixel is a sixteenth of a block in model
	 * space, the same unit the model itself is drawn in; the rotation is about the head, since the
	 * origin here is the head centre.
	 */
	private static void applyAuthoringTransforms(
			PoseStack poseStack,
			CosmeticSettings settings,
			HeadDisplay.Transform display
	) {
		boolean fromDisplay = display != null && !display.isNone();
		CosmeticSettings.Vec3 offset = fromDisplay ? display.offset() : settings.offset();
		poseStack.translate(offset.x() / 16.0F, offset.y() / 16.0F, offset.z() / 16.0F);

		CosmeticSettings.Vec3 rotation = fromDisplay ? display.rotation() : settings.rotation();

		if (rotation.x() != 0.0F) {
			poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x()));
		}

		if (rotation.y() != 0.0F) {
			poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y()));
		}

		if (rotation.z() != 0.0F) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
		}

		float scale = fromDisplay ? display.scale() : settings.scale();

		if (scale != 1.0F) {
			poseStack.scale(scale, scale, scale);
		}
	}

	/**
	 * The item path: vanilla resolves the model into a render state and submits it, and the model's own
	 * {@code display.head} entry is what places it. The glow pass repeats that at full bright so an
	 * emissive copy draws over the normal one instead of replacing it.
	 */
	private void submitItem(
			String name,
			CosmeticSettings settings,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int light
	) {
		ItemModel model = CosmeticIndex.forPlayer(name, settings.itemModel());

		if (model == null) {
			return;
		}

		ItemStackRenderState cosmetics = new ItemStackRenderState();

		// The model instance already came from the config's id, so the stack is only context for tint
		// and dynamic overrides; passing it through keeps vanilla's resolve path intact. HEAD is what
		// selects the model's display.head entry, so this call is the whole placement.
		model.update(cosmetics, NO_ITEM, this.resolver(), ItemDisplayContext.HEAD, Minecraft.getInstance().level, null, 0);

		if (cosmetics.isEmpty()) {
			return;
		}

		cosmetics.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);

		if (PseudoHat.DEBUG) {
			PseudoHat.LOGGER.info("[layer] {} head reference applied, baked bbox={}", name,
					cosmetics.getModelBoundingBox());
		}

		if (settings.glow()) {
			ItemModel glowModel = CosmeticIndex.model(settings.glowModel());

			if (glowModel == null) {
				return;
			}

			ItemStackRenderState glow = new ItemStackRenderState();
			glowModel.update(glow, NO_ITEM, this.resolver(), ItemDisplayContext.HEAD, Minecraft.getInstance().level, null, 0);

			if (!glow.isEmpty()) {
				// Full bright only: the pass is identical to the first one otherwise, so the two sum to
				// an emissive copy rather than a flicker.
				glow.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			}
		}
	}

	/** One resolver per layer: it is a stateless view over the model manager, but not free to make. */
	private ItemModelResolver resolver() {
		ItemModelResolver current = this.resolver;

		if (current == null) {
			current = new ItemModelResolver(Minecraft.getInstance().getModelManager());
			this.resolver = current;
		}

		return current;
	}

	private static boolean isHeadEmpty(HumanoidRenderState state) {
		return state.headEquipment == null || state.headEquipment.isEmpty();
	}
}
