package dev.pseudohat.mixin;

import dev.pseudohat.CosmeticContext;
import dev.pseudohat.render.HasCosmeticState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the rendered entity's name and state snapshot across to the render layer. */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements HasCosmeticState {
	@Unique
	@Nullable
	private String pseudohat$name;

	@Unique
	private CosmeticContext pseudohat$context = CosmeticContext.IDLE;

	@Override
	@Nullable
	public String pseudohat$name() {
		return this.pseudohat$name;
	}

	@Override
	public void pseudohat$setName(@Nullable String name) {
		this.pseudohat$name = name;
	}

	@Override
	public CosmeticContext pseudohat$context() {
		return this.pseudohat$context;
	}

	@Override
	public void pseudohat$setContext(CosmeticContext context) {
		this.pseudohat$context = context;
	}
}
