package dev.pseudohat;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The trigger names a settings file may use under {@code "events"}.
 *
 * <p>Each constant carries its own predicate over {@link CosmeticContext}, so adding an event means
 * adding a constant here and nothing else: the settings parser, the animation controller and the tests
 * all resolve the trigger through {@link #isActive(CosmeticContext)}.
 *
 * <p>Keys are matched case-insensitively and with or without underscores, so {@code onSneak},
 * {@code ON_SNEAK} and {@code on_sneak} are the same event. The camel case form is the documented one.
 */
public enum CosmeticEvent {
	ON_SNEAK("onSneak", CosmeticContext::sneaking),
	ON_SPRINT("onSprint", CosmeticContext::sprinting),
	ON_SWIM("onSwim", CosmeticContext::swimming),
	ON_IN_WATER("onInWater", CosmeticContext::inWater),
	ON_FALL_FLYING("onFallFlying", CosmeticContext::fallFlying),
	ON_USE_ITEM("onUseItem", CosmeticContext::usingItem),
	ON_HURT("onHurt", CosmeticContext::hurt),
	ON_JUMP("onJump", CosmeticContext::ascending),
	ON_SWING("onSwing", CosmeticContext::swinging),
	ON_WALK("onWalk", context -> context.walkSpeed() > CosmeticContext.WALK_THRESHOLD),
	ON_DEATH("onDeath", CosmeticContext::dead);

	private static final Map<String, CosmeticEvent> BY_KEY = new HashMap<>();

	static {
		for (CosmeticEvent event : values()) {
			BY_KEY.put(normalize(event.key), event);
		}
	}

	private final String key;
	private final Predicate<CosmeticContext> active;

	CosmeticEvent(String key, Predicate<CosmeticContext> active) {
		this.key = key;
		this.active = active;
	}

	/** The documented spelling, as written in a settings file. */
	public String key() {
		return this.key;
	}

	/** Whether this trigger fires for the given snapshot. */
	public boolean isActive(CosmeticContext context) {
		return this.active.test(context);
	}

	/**
	 * Resolves a settings file key, or {@code null} when the key names no event.
	 *
	 * <p>An unknown key must not throw: a settings file written against a newer version of the mod
	 * should load with a warning rather than take the whole config down.
	 */
	@Nullable
	public static CosmeticEvent fromKey(String key) {
		return BY_KEY.get(normalize(key));
	}

	private static String normalize(String key) {
		return key.replace("_", "").toLowerCase(Locale.ROOT);
	}
}
