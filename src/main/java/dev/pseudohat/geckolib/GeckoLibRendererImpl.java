package dev.pseudohat.geckolib;

import dev.pseudohat.CosmeticSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads GeckoLib only when it is there, and owns everything GeckoLib-shaped: the models, renderers and
 * per-wearer animatables that must not outlive a resource reload.
 *
 * <p>This class is reached by name from {@link GeckoLibBridge} and is deliberately the only class in
 * the mod whose field and parameter types are GeckoLib's, so a machine without GeckoLib never has to
 * resolve a single one of them. It is public only so the bridge can construct it by reflection.
 */
public final class GeckoLibRendererImpl implements GeckoLibBridge.Renderer {
	/** Per cosmetic id: the shared model and renderer, rebuilt after every resource reload. */
	private final Map<Identifier, Renderers> renderers = new HashMap<>();

	/** Per cosmetic id and wearer: the animation state, so each player's triggers are independent. */
	private final Map<String, HatAnimatable> animatables = new HashMap<>();

	@Override
	public void render(GeckoLibBridge.Request request) {
		CosmeticSettings settings = request.settings();
		Renderers entry = this.renderers.computeIfAbsent(settings.id(), id -> createRenderers(settings));
		HatAnimatable animatable = this.animatables.computeIfAbsent(
				key(settings, request.wearer()),
				k -> new HatAnimatable(settings, entry.model())
		);

		animatable.setContext(request.context());

		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		entry.renderer().performRenderPass(
				animatable, null, request.pose(), request.collector(), request.camera(), request.light(), partialTick
		);
	}

	@Override
	public void invalidate() {
		// The models hold baked geometry and textures from the old pack, and the animatables remember
		// which animations existed in the old file, so neither may be carried across a reload.
		this.renderers.clear();
		this.animatables.clear();
	}

	/** A space separates the halves; a cosmetic id can never contain one, so keys cannot collide. */
	private static String key(CosmeticSettings settings, String wearer) {
		return settings.id() + " " + wearer;
	}

	private static Renderers createRenderers(CosmeticSettings settings) {
		HatGeoModel model = new HatGeoModel(settings);
		return new Renderers(model, new HatGeoRenderer(model, settings));
	}

	private record Renderers(HatGeoModel model, HatGeoRenderer renderer) {
	}
}
