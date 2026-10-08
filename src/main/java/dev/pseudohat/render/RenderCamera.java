package dev.pseudohat.render;

import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * The camera that belongs to the entity currently being submitted.
 *
 * <p>Layers never receive the camera: {@code LivingEntityRenderer.submit} does, and it runs the layer
 * list inside its own frame, so stashing the camera at {@code submit}'s head and reading it while a
 * layer draws gives exactly that layer the camera it is being drawn against. The value is only valid
 * between a submit and its layers, which is the entire window anything here needs it for.
 */
public final class RenderCamera {
	private static final CameraRenderState FALLBACK = new CameraRenderState();
	private static CameraRenderState current = FALLBACK;

	private RenderCamera() {
	}

	public static CameraRenderState get() {
		return current;
	}

	public static void set(CameraRenderState camera) {
		current = camera == null ? FALLBACK : camera;
	}
}
