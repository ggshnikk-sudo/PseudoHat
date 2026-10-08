package dev.pseudohat.geckolib;

import com.geckolib.constant.DataTickets;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import dev.pseudohat.CosmeticContext;
import dev.pseudohat.CosmeticSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws one cosmetic's geometry inside the pose the render layer already set up.
 *
 * <p>Two things are deliberately different from a normal GeckoLib object renderer:
 *
 * <ul>
 *   <li>{@link #adjustRenderPose} does nothing. GeckoLib's own default shifts an object to the centre
 *       of a block, which is right for a dropped item and wrong for a hat: the pose this renderer is
 *       handed is already vanilla's head frame, so translating it again would move the model off the
 *       head.</li>
 *   <li>The glow settings from the file become a second render layer, either GeckoLib's own
 *       {@code <texture>_glowmask.png} pass or a supplied texture drawn full bright.</li>
 * </ul>
 */
final class HatGeoRenderer extends GeoObjectRenderer<HatAnimatable, Void, GeoRenderState> {
	private final CosmeticSettings settings;

	HatGeoRenderer(GeoModel<HatAnimatable> model, CosmeticSettings settings) {
		super(model);
		this.settings = settings;

		if (settings.glow()) {
			if (settings.glowTexture() != null) {
				// A dedicated emissive texture: drawn as the vanilla "eyes" render type, which ignores
				// world light the same way a glowmask pass does.
				this.withRenderLayer(new TextureLayerGeoLayer<>(
						this, settings.glowTexture(), RenderTypes::eyes
				));
			} else if (hasAutoGlowMask(settings)) {
				this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
			} else {
				// No emissive mask authored for the model: reuse the base texture at full bright so the
				// whole model glows, instead of requiring the author to duplicate it as a glowmask.
				this.withRenderLayer(new TextureLayerGeoLayer<>(
						this, settings.texture(), RenderTypes::eyes
				));
			}
		}
	}

	/** Whether GeckoLib's auto-glow convention (<texture>_glowmask.png) resolves for this cosmetic. */
	private static boolean hasAutoGlowMask(CosmeticSettings settings) {
		Identifier texture = settings.texture();
		Identifier mask = Identifier.fromNamespaceAndPath(
				texture.getNamespace(), texture.getPath().replace(".png", "_glowmask.png")
		);
		return Minecraft.getInstance().getResourceManager().getResource(mask).isPresent();
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		// Placement happened in PseudoHatLayer, so the head frame the pose arrived in is the origin.
	}

	@Override
	public RenderType getRenderType(
			GeoRenderState renderState, Identifier texture
	) {
		// GeckoLib's default is entityCutout, which alpha-tests instead of blending: semi-transparent
		// smoke renders opaque. A pack that authors soft edges opts into translucent blending.
		return settings.transparent()
				? RenderTypes.entityTranslucent(texture)
				: super.getRenderType(renderState, texture);
	}

	@Override
	public void addRenderData(
			HatAnimatable animatable, Void relatedObject, GeoRenderState renderState, float partialTick
	) {
		// GeckoLib defaults this to false, which makes molang's query.is_moving lie about a walking
		// wearer; the render layer is the only place that sees both the state and the entity.
		renderState.addGeckolibData(
				DataTickets.IS_MOVING,
				animatable.context().walkSpeed() > CosmeticContext.WALK_THRESHOLD
		);
	}
}
