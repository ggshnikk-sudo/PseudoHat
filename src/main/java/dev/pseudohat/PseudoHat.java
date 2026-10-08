package dev.pseudohat;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

/**
 * PseudoHat renders resource-pack item models on players' heads.
 *
 * <p>There is no server side and no networking: which model a player wears comes from the
 * nickname-to-model mapping in {@code assets/pseudohat/config/*.json}, read by {@link HatConfig}.
 * That means it works on unmodified vanilla servers, at the cost of only being visible to the client
 * that installed the pack.
 */
public final class PseudoHat implements ClientModInitializer {
	public static final String MOD_ID = "pseudohat";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Enabled with {@code -Dpseudohat.debug=true}; drives the per-frame layer trace. */
	public static final boolean DEBUG = Boolean.getBoolean("pseudohat.debug");

	@Override
	public void onInitializeClient() {
		// Reload is handled by MinecraftMixin rather than Fabric API's resource loader events, so the
		// mod needs nothing but the loader itself.
		LOGGER.info(
				"[{}] ready; map a nickname to an item model in assets/{}/{}/<any-name>.json",
				MOD_ID, HatConfig.NAMESPACE, "config"
		);
	}
}