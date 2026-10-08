package dev.pseudohat.mixin;

import dev.pseudohat.CosmeticContext;
import dev.pseudohat.render.HasCosmeticState;
import dev.pseudohat.render.PseudoHatLayer;
import dev.pseudohat.render.RenderCamera;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class LivingEntityRendererMixin {
	@Shadow
	protected abstract boolean addLayer(RenderLayer layer);

	@Inject(method = "<init>", at = @At("TAIL"))
	private void pseudohat$addLayer(
			EntityRendererProvider.Context context,
			EntityModel<?> model,
			float shadowRadius,
			CallbackInfo ci
	) {
		if (model instanceof HumanoidModel<?>) {
			// The renderer is only known to be LivingEntityRenderState here, while the layer needs
			// HumanoidRenderState; that is safe because submit() re-checks the state type per entity.
			@SuppressWarnings("unchecked")
			RenderLayerParent<?, ?> parent = (RenderLayerParent<?, ?>) (Object) this;
			this.addLayer(new PseudoHatLayer(parent));
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"))
	private void pseudohat$extractCosmetic(
			LivingEntity entity,
			LivingEntityRenderState state,
			float partialTick,
			CallbackInfo ci
	) {
		HasCosmeticState target = (HasCosmeticState) state;

		if (entity instanceof Player player) {
			target.pseudohat$setName(player.getName().getString());
			target.pseudohat$setContext(pseudohat$context(player, state));
		} else {
			// Not a player, so no name and nothing to react to: the shared idle record, no allocation.
			target.pseudohat$setName(null);
			target.pseudohat$setContext(CosmeticContext.IDLE);
		}
	}

	/** Snapshot the live entity into an immutable record while the state is being filled. */
	@Unique
	private static CosmeticContext pseudohat$context(LivingEntity entity, LivingEntityRenderState state) {
		return new CosmeticContext(
				entity.isCrouching(),
				entity.isSprinting(),
				entity.isVisuallySwimming(),
				entity.isInWater(),
				entity.isFallFlying(),
				entity.isUsingItem(),
				entity.hurtTime > 0,
				// Jumping rather than merely airborne: rising while off the ground reads as a jump
				// without firing for every long fall.
				!entity.onGround() && entity.getDeltaMovement().y > 0.05D,
				entity.swinging,
				state.deathTime > 0.0F,
				state.walkAnimationSpeed
		);
	}

	/**
	 * Stashes the camera so layers can read it: layers are called inside submit and never see the
	 * camera themselves, and anything drawn this frame must use this frame's view.
	 */
	@Inject(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD")
	)
	private void pseudohat$stashCamera(
			LivingEntityRenderState state,
			com.mojang.blaze3d.vertex.PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState camera,
			CallbackInfo ci
	) {
		RenderCamera.set(camera);
	}
}
