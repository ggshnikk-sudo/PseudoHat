package dev.pseudohat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.pseudohat.CosmeticContext;
import dev.pseudohat.CosmeticSettings;
import dev.pseudohat.PseudoHat;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;

/**
 * The one place the mod is allowed to touch GeckoLib.
 *
 * <p>GeckoLib is an optional dependency: the mod must build, load and run without it, and a pack that
 * declares a {@code geckolib} cosmetic on a machine that does not have it must degrade to "nothing
 * drawn" rather than crash. The way that is kept honest is that nothing outside this file and the
 * {@code GeckoLibRendererImpl} class in this package mentions a GeckoLib type at all: the implementation
 * is loaded by name, only after {@code com.geckolib.GeckoLib} has been found on the classpath, so the
 * JVM never resolves a class it cannot find.
 *
 * <p>Callers see {@link #render(Request)}, which is a silent no-op when GeckoLib is absent.
 */
public final class GeckoLibBridge {
	/**
	 * Everything a frame needs, assembled by the render layer while its pose is live.
	 *
	 * @param wearer   the player's nickname, which is what keys the per-wearer animation state
	 * @param settings the cosmetic's declared settings, already resolved from the config
	 * @param context  the wearer snapshot captured during extraction
	 * @param pose     the pose at the head, inside the layer's push, ready to draw at the origin
	 * @param collector where the frame's render nodes go
	 * @param light    packed light coords for the entity
	 * @param camera   this frame's camera, stashed by the renderer mixin because layers never see it
	 */
	public record Request(
			String wearer,
			CosmeticSettings settings,
			CosmeticContext context,
			PoseStack pose,
			SubmitNodeCollector collector,
			int light,
			CameraRenderState camera
	) {
	}

	/** Implemented only by {@link GeckoLibRendererImpl}, which is loaded reflectively. */
	interface Renderer {
		void render(Request request);

		/** Drops everything cached against the previous resource pack. */
		void invalidate();
	}

	private static final String GECKOLIB_MAIN = "com.geckolib.GeckoLib";
	private static final String IMPL_CLASS = "dev.pseudohat.geckolib.GeckoLibRendererImpl";

	private static boolean probed;
	@Nullable
	private static Renderer renderer;

	private GeckoLibBridge() {
	}

	/** Draws the cosmetic, or does nothing at all when GeckoLib is not installed. */
	public static void render(Request request) {
		Renderer impl = resolve();

		if (impl == null) {
			return;
		}

		try {
			impl.render(request);
		} catch (Throwable t) {
			// A failure here would repeat every frame for every wearer; report it once and stop trying.
			renderer = null;
			PseudoHat.LOGGER.error("GeckoLib cosmetic rendering failed, hiding geckolib cosmetics", t);
		}
	}

	/** Whether a geckolib cosmetic would actually draw right now. */
	public static boolean isAvailable() {
		return resolve() != null;
	}

	/** Called after resource packs reload, so the implementation drops models and animatables. */
	public static void invalidate() {
		Renderer impl = renderer;

		if (impl != null) {
			try {
				impl.invalidate();
			} catch (Throwable t) {
				PseudoHat.LOGGER.error("Failed to drop GeckoLib caches after reload", t);
			}
		}
	}

	@Nullable
	private static synchronized Renderer resolve() {
		if (probed) {
			return renderer;
		}

		probed = true;

		if (!isInstalled(GECKOLIB_MAIN)) {
			PseudoHat.LOGGER.info(
					"GeckoLib is not installed; '{}' cosmetics will not draw", CosmeticSettings.Type.GECKOLIB.key()
			);
			return renderer;
		}

		try {
			Class<?> implementation = Class.forName(IMPL_CLASS);
			renderer = (Renderer) implementation.getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			PseudoHat.LOGGER.error("GeckoLib is installed but the geckolib render path failed to load", t);
			renderer = null;
		}

		return renderer;
	}

	/** A presence probe, not a load: {@code initialize=false} keeps the class body untouched. */
	private static boolean isInstalled(String className) {
		try {
			Class.forName(className, false, GeckoLibBridge.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}
}
