package dev.pseudohat.geckolib;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.pseudohat.CosmeticSettings;
import net.minecraft.resources.Identifier;

/**
 * The geometry, texture and animation file for one cosmetic.
 *
 * <p>All three ids come straight from the settings file, so the mapping a pack author writes is the
 * mapping GeckoLib reads: {@code geo} names the {@code .geo.json}, {@code texture} the diffuse texture
 * and {@code animations} the {@code .animation.json}. Each defaults to the cosmetic's own id, which is
 * the same convention GeckoLib itself uses when an id is written as {@code ns:name}.
 */
final class HatGeoModel extends GeoModel<HatAnimatable> {
	private final CosmeticSettings settings;

	HatGeoModel(CosmeticSettings settings) {
		this.settings = settings;
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return this.settings.geo();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return this.settings.texture();
	}

	@Override
	public Identifier getAnimationResource(HatAnimatable animatable) {
		return this.settings.animations();
	}
}
