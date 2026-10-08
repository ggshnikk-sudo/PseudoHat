package dev.pseudohat.geckolib;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.model.GeoModel;
import com.geckolib.util.GeckoLibUtil;
import dev.pseudohat.CosmeticContext;
import dev.pseudohat.CosmeticSettings;
import dev.pseudohat.PseudoHat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The thing being drawn: one instance per cosmetic per wearer, holding that wearer's animation state.
 *
 * <p>The controller's state handler is where the settings file becomes behaviour. It runs once per
 * frame while the cosmetic is on screen, and picks what to play in this order:
 *
 * <ol>
 *   <li>a latched {@code once} animation, until GeckoLib reports it finished;</li>
 *   <li>the first event rule whose trigger holds, in the order the file declares them;</li>
 *   <li>the {@code default} animation, looping;</li>
 *   <li>otherwise nothing at all, with the controller cleared so the model sits in its bind pose.</li>
 * </ol>
 *
 * <p>The two rules that make this behave predictably are worth spelling out. GeckoLib's
 * {@code setAnimation} ignores a request that matches what is already playing, so replaying an
 * animation needs the controller cleared first, which is what the latch and the final branch do. And
 * returning {@link PlayState#STOP} without clearing does not actually stop a looping animation: it
 * holds the last frame and keeps looping, so "rest in bind pose" has to go through
 * {@link AnimationController#reset()}.
 *
 * <p>Animations named by the file are checked against the animation file once, the first time they are
 * wanted. A typo then costs one warning and is skipped rather than an error line from GeckoLib every
 * frame for as long as the cosmetic is visible.
 */
final class HatAnimatable implements GeoAnimatable {
	/** A fifth of a second of blend between animations; GeckoLib's own default is no blend at all. */
	private static final int TRANSITION_TICKS = 4;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final CosmeticSettings settings;
	private final GeoModel<HatAnimatable> model;
	private final Map<String, Boolean> available = new HashMap<>();
	private final boolean[] ruleActive;

	/** The wearer snapshot for this frame, swapped in by the render pass before it draws. */
	private volatile CosmeticContext context = CosmeticContext.IDLE;

	/** The {@code once} animation currently playing out, or {@code null} when nothing is latched. */
	private String latched;

	/** Last animation named to the debug log, so the trace shows decisions rather than frames. */
	private String lastLogged;

	private boolean animationsChecked;

	HatAnimatable(CosmeticSettings settings, GeoModel<HatAnimatable> model) {
		this.settings = settings;
		this.model = model;
		this.ruleActive = new boolean[settings.events().size()];
	}

	void setContext(CosmeticContext context) {
		this.context = context;
	}

	CosmeticContext context() {
		return this.context;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("pseudohat", TRANSITION_TICKS, this::decide));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	private PlayState decide(AnimationTest<HatAnimatable> test) {
		AnimationController<HatAnimatable> controller = test.controller();

		if (!this.animationsChecked) {
			checkAnimations();
		}

		// A latched once-animation owns the controller until it ends; nothing may interrupt it.
		if (this.latched != null) {
			if (controller.hasAnimationFinished()) {
				this.latched = null;
			} else {
				return log(this.latched, test.setAndContinue(RawAnimation.begin().then(this.latched, LoopType.PLAY_ONCE)));
			}
		}

		List<CosmeticSettings.EventRule> rules = this.settings.events();
		CosmeticSettings.EventRule chosen = null;

		// Every rule's held/released edge is updated on every frame, not just the chosen one, or a rule
		// that sits behind an active one would miss the edge that lets its own once-animation fire.
		for (int i = 0; i < rules.size(); i++) {
			CosmeticSettings.EventRule rule = rules.get(i);
			boolean active = rule.event().isActive(this.context) && isAvailable(rule.animation());
			boolean rising = active && !this.ruleActive[i];
			this.ruleActive[i] = active;

			if (chosen == null && active && (!rule.once() || rising)) {
				chosen = rule;
			}
		}

		if (chosen != null) {
			String animation = chosen.animation();

			if (chosen.once()) {
				this.latched = animation;
				return log(animation, test.setAndContinue(RawAnimation.begin().then(animation, LoopType.PLAY_ONCE)));
			}

			return log(animation, test.setAndContinue(RawAnimation.begin().thenLoop(animation)));
		}

		String fallback = this.settings.defaultAnimation();

		if (fallback != null && isAvailable(fallback)) {
			return log(fallback, test.setAndContinue(RawAnimation.begin().thenLoop(fallback)));
		}

		// Nothing applies. Clearing first is what actually stops a looping animation; a bare STOP would
		// leave it running, because GeckoLib only stops advancing the timeline, not the pose.
		this.latched = null;
		controller.reset();
		return log(null, PlayState.STOP);
	}

	/**
	 * Reports the animation {@link #decide} settled on, once per change, so the log reads as decisions
	 * (idle, then run, then hurt) instead of one line per frame.
	 */
	private PlayState log(String animation, PlayState state) {
		if (PseudoHat.DEBUG && !java.util.Objects.equals(animation, this.lastLogged)) {
			this.lastLogged = animation;
			PseudoHat.LOGGER.info(
					"[anim] {} {} -> {}", this.settings.id(), this.context.walkSpeed() > CosmeticContext.WALK_THRESHOLD
							? "moving" : "still",
					animation == null ? "<bind pose>" : animation
			);
		}

		return state;
	}

	/**
	 * Whether {@code animation} exists in this cosmetic's animation file, asked once and remembered.
	 *
	 * <p>GeckoLib itself answers a missing name by logging an error on every lookup, which would be one
	 * line per frame per wearer for as long as the cosmetic is visible. Asking here turns that into a
	 * single warning that names the cosmetic, and lets {@link #decide} fall through to the next rule.
	 */
	private boolean isAvailable(String animation) {
		Boolean known = this.available.get(animation);

		if (known != null) {
			return known;
		}

		boolean exists = this.model.getBakedAnimation(this, animation) != null;
		this.available.put(animation, exists);

		if (!exists) {
			PseudoHat.LOGGER.warn(
					"Cosmetic {}: animation '{}' is not in {}",
					this.settings.id(), animation, this.settings.animations()
			);
		}

		return exists;
	}

	/** Asks for every configured animation up front, so a bad file is reported as one block. */
	private void checkAnimations() {
		this.animationsChecked = true;

		if (this.settings.defaultAnimation() != null) {
			isAvailable(this.settings.defaultAnimation());
		}

		for (CosmeticSettings.EventRule rule : this.settings.events()) {
			isAvailable(rule.animation());
		}
	}
}
