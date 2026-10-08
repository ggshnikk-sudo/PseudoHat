package dev.pseudohat;

/**
 * Snapshot of the wearer's state, taken while the render state is extracted.
 *
 * <p>Render states are pooled, so anything the animation logic needs from the live entity has to be
 * copied out during extraction rather than looked up later from a reference to the state. The record
 * is immutable on purpose: it is written once per frame on the render thread and then read by the
 * animation controller while the geckolib pass runs, so there is no window where it can change
 * underneath a reader.
 *
 * <p>Only players ever get a non-idle instance, so the common case for every other living entity is a
 * shared constant and no allocation at all.
 *
 * @param sneaking    the wearer is crouching
 * @param sprinting   the wearer is sprinting
 * @param swimming    the wearer is visually swimming (or posed for a fall-flying-like swim pose)
 * @param inWater     the wearer is in water
 * @param fallFlying  the wearer is elytra flying
 * @param usingItem   the wearer is actively using an item (bow, food, shield, ...)
 * @param hurt        the wearer is inside their hurt time, i.e. has just taken damage
 * @param ascending   the wearer is moving upward in the air, i.e. mid-jump
 * @param swinging    the wearer is mid arm swing (attack)
 * @param dead         the wearer's death animation has started
 * @param walkSpeed   the animated walk speed, 0 when standing still; {@code onWalk} uses this
 */
public record CosmeticContext(
		boolean sneaking,
		boolean sprinting,
		boolean swimming,
		boolean inWater,
		boolean fallFlying,
		boolean usingItem,
		boolean hurt,
		boolean ascending,
		boolean swinging,
		boolean dead,
		float walkSpeed
) {
	/** Shared value for entities that are not players, so extraction allocates only for players. */
	public static final CosmeticContext IDLE = new CosmeticContext(
			false, false, false, false, false, false, false, false, false, false, 0.0F
	);

	/** Anything above this is animated walking rather than standing or a barely-ticking walk cycle. */
	public static final float WALK_THRESHOLD = 0.05F;
}
