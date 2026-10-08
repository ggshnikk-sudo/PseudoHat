package dev.pseudohat.render;

import dev.pseudohat.CosmeticContext;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by the mixin on {@code LivingEntityRenderState}: render states are pooled and reused
 * every frame, so everything the layer needs about the wearer is copied onto the state during
 * extraction rather than being carried through the renderer.
 *
 * <p>Both values are written once per frame while the state is being filled and read later in the
 * same frame while layers submit, which is also why they must be reset rather than left over from the
 * previous entity that happened to use this pooled state.
 */
public interface HasCosmeticState {
	@Nullable
	String pseudohat$name();

	void pseudohat$setName(@Nullable String name);

	/** Never {@code null}: entities that cannot wear a cosmetic get {@link CosmeticContext#IDLE}. */
	CosmeticContext pseudohat$context();

	void pseudohat$setContext(CosmeticContext context);
}
