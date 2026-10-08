package dev.pseudohat.mixin;

import dev.pseudohat.CosmeticIndex;
import dev.pseudohat.CosmeticSettingsIndex;
import dev.pseudohat.HatConfig;
import dev.pseudohat.HeadDisplay;
import dev.pseudohat.geckolib.GeckoLibBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Drops the hat config and the baked model cache whenever resource packs (re)load.
 *
 * <p>This is the whole reload hook. Fabric API's resource loader events are deliberately not used, so
 * the mod has no dependency beyond the loader itself.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "reloadResourcePacks", at = @At("RETURN"))
	private void pseudohat$invalidateOnReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		// The future completes once the new packs are in place, which is when a rebake is valid.
		cir.getReturnValue().thenRun(() -> {
			HatConfig.invalidate();
			CosmeticSettingsIndex.invalidate();
			CosmeticIndex.invalidate();
			HeadDisplay.invalidate();
			GeckoLibBridge.invalidate();
		});
	}
}