package dev.pseudohat;

import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings parser is a pure function over Gson precisely so it can be pinned down here without a
 * running game: these tests are the contract a pack author relies on, including what happens when a
 * file is wrong rather than whether it loads.
 */
class CosmeticSettingsTest {
	private static final Identifier FILE = Identifier.fromNamespaceAndPath("pseudohat", "config/models/hat.json");

	private static CosmeticSettings.Result parse(String json) {
		return CosmeticSettings.parse(FILE, StrictJsonParser.parse(json));
	}

	private static CosmeticSettings only(CosmeticSettings.Result result) {
		assertEquals(1, result.cosmetics().size(), () -> "expected one setting, got " + result.cosmetics().keySet());
		return result.cosmetics().values().iterator().next();
	}

	@Test
	@DisplayName("a file without a cosmetics object reports itself as undeclared")
	void missingCosmeticsKey() {
		CosmeticSettings.Result result = parse("{\"hats\":{}}");

		assertFalse(result.declared());
		assertTrue(result.cosmetics().isEmpty());
		assertFalse(result.problems().isEmpty());
	}

	@Test
	@DisplayName("an entry may be empty and still mean the id's own item model")
	void emptyEntryUsesItsOwnId() {
		CosmeticSettings settings = only(parse("{\"cosmetics\":{\"pseudohat:poppy\":{}}}"));

		assertEquals(CosmeticSettings.Type.ITEM, settings.type());
		assertEquals(Identifier.parse("pseudohat:poppy"), settings.itemModel());
		assertFalse(settings.glow());
		assertTrue(settings.events().isEmpty());
		assertNull(settings.defaultAnimation());
		assertEquals(1.0F, settings.scale());
		assertEquals(CosmeticSettings.Vec3.ZERO, settings.offset());
		assertEquals(CosmeticSettings.Vec3.ZERO, settings.rotation());
	}

	@Test
	@DisplayName("geckolib defaults: geometry, animations and texture all derive from the entry id")
	void geckolibDefaults() {
		CosmeticSettings settings = only(parse("{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\"}}}"));

		assertEquals(CosmeticSettings.Type.GECKOLIB, settings.type());
		assertEquals(Identifier.parse("pseudohat:hat"), settings.geo());
		assertEquals(Identifier.parse("pseudohat:hat"), settings.animations());
		assertEquals(Identifier.parse("pseudohat:textures/entity/hat.png"), settings.texture());
		// The item model id stays the entry id so a mislabelled type degrades to something loadable.
		assertEquals(Identifier.parse("pseudohat:hat"), settings.itemModel());
	}

	@Test
	@DisplayName("explicit geometry, texture and animations override the derived ones")
	void geckolibExplicitIds() {
		CosmeticSettings settings = only(parse("""
				{"cosmetics":{"pseudohat:crown":{"type":"geckolib",
					"geo":"pseudohat:models/crown",
					"texture":"pseudohat:textures/entity/crown.png",
					"animations":"pseudohat:crown_moves"}}}
				"""));

		assertEquals(Identifier.parse("pseudohat:models/crown"), settings.geo());
		assertEquals(Identifier.parse("pseudohat:textures/entity/crown.png"), settings.texture());
		assertEquals(Identifier.parse("pseudohat:crown_moves"), settings.animations());
	}

	@Test
	@DisplayName("scale, offset and rotation parse as numbers and stay finite")
	void numericFields() {
		CosmeticSettings settings = only(parse("""
				{"cosmetics":{"pseudohat:hat":{"scale":1.5,"offset":[1,-2,3],"rotation":[90,0,180]}}}
				"""));

		assertEquals(1.5F, settings.scale());
		assertEquals(new CosmeticSettings.Vec3(1.0F, -2.0F, 3.0F), settings.offset());
		assertEquals(new CosmeticSettings.Vec3(90.0F, 0.0F, 180.0F), settings.rotation());
	}

	@Test
	@DisplayName("a non-positive or non-numeric scale falls back to 1 instead of collapsing the model")
	void badScaleFallsBack() {
		CosmeticSettings.Result result = parse("{\"cosmetics\":{\"pseudohat:hat\":{\"scale\":-4}}}");

		assertEquals(1.0F, only(result).scale());
		assertFalse(result.problems().isEmpty());
	}

	@Test
	@DisplayName("a malformed offset falls back to zero rather than shifting the model")
	void badOffsetFallsBack() {
		CosmeticSettings.Result offsetOfTwo = parse("{\"cosmetics\":{\"pseudohat:hat\":{\"offset\":[1,2]}}}");
		CosmeticSettings.Result offsetOfText = parse("{\"cosmetics\":{\"pseudohat:hat\":{\"offset\":\"up\"}}}");

		assertEquals(CosmeticSettings.Vec3.ZERO, only(offsetOfTwo).offset());
		assertEquals(CosmeticSettings.Vec3.ZERO, only(offsetOfText).offset());
		assertFalse(offsetOfTwo.problems().isEmpty());
		assertFalse(offsetOfText.problems().isEmpty());
	}

	@Test
	@DisplayName("glow as a boolean and as an object with a model both enable the item glow pass")
	void glowForms() {
		CosmeticSettings flag = only(parse("{\"cosmetics\":{\"pseudohat:hat\":{\"glow\":true}}}"));
		CosmeticSettings withModel = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"glow\":{\"model\":\"pseudohat:hat_glow\"}}}}"
		));
		CosmeticSettings off = only(parse("{\"cosmetics\":{\"pseudohat:hat\":{\"glow\":false}}}"));

		assertTrue(flag.glow());
		assertNull(flag.glowModel());
		assertTrue(withModel.glow());
		assertEquals(Identifier.parse("pseudohat:hat_glow"), withModel.glowModel());
		assertFalse(off.glow());
	}

	@Test
	@DisplayName("a geckolib glow object carries its own glow texture")
	void geckolibGlowTexture() {
		CosmeticSettings settings = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\",\"glow\":{\"texture\":\"pseudohat:textures/entity/hat_glow.png\"}}}}"
		));

		assertTrue(settings.glow());
		assertEquals(Identifier.parse("pseudohat:textures/entity/hat_glow.png"), settings.glowTexture());
		assertNull(settings.glowModel());
	}

	@Test
	@DisplayName("transparent flags the model for translucent blending, anything else stays cutout")
	void transparentFlag() {
		CosmeticSettings on = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\",\"transparent\":true}}}"
		));
		CosmeticSettings off = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\",\"transparent\":false}}}"
		));
		CosmeticSettings defaulted = only(parse("{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\"}}}"));

		assertTrue(on.transparent());
		assertFalse(off.transparent());
		assertFalse(defaulted.transparent());
	}

	@Test
	@DisplayName("a non-boolean transparent is reported as a problem and treated as off")
	void transparentBadType() {
		CosmeticSettings.Result result = parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"transparent\":\"yes\"}}}"
		);
		CosmeticSettings settings = only(result);

		assertFalse(settings.transparent());
		assertFalse(result.problems().isEmpty());
		assertTrue(result.problems().get(0).contains("transparent"));
	}

	@Test
	@DisplayName("a bare event string loops while the trigger holds")
	void eventStringForm() {
		CosmeticSettings settings = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"events\":{\"onSneak\":\"animation.hat.sneak\"}}}}"
		));

		assertEquals(1, settings.events().size());
		CosmeticSettings.EventRule rule = settings.events().get(0);
		assertEquals(CosmeticEvent.ON_SNEAK, rule.event());
		assertEquals("animation.hat.sneak", rule.animation());
		assertFalse(rule.once());
	}

	@Test
	@DisplayName("an event object may ask for a single play of a non-looping animation")
	void eventOnceForm() {
		CosmeticSettings settings = only(parse("""
				{"cosmetics":{"pseudohat:hat":{"events":{
					"onHurt":{"animation":"animation.hat.hurt","mode":"once"}}}}}
				"""));

		CosmeticSettings.EventRule rule = settings.events().get(0);
		assertEquals(CosmeticEvent.ON_HURT, rule.event());
		assertEquals("animation.hat.hurt", rule.animation());
		assertTrue(rule.once());
	}

	@Test
	@DisplayName("rules play in the order they are written, which is their priority")
	void ruleOrderIsDeclarationOrder() {
		CosmeticSettings settings = only(parse("""
				{"cosmetics":{"pseudohat:hat":{"events":{
					"onDeath":"animation.hat.die",
					"onSprint":"animation.hat.run",
					"onSneak":"animation.hat.sneak"}}}}
				"""));

		assertEquals(
				List.of(CosmeticEvent.ON_DEATH, CosmeticEvent.ON_SPRINT, CosmeticEvent.ON_SNEAK),
				settings.events().stream().map(CosmeticSettings.EventRule::event).toList()
		);
	}

	@Test
	@DisplayName("an unknown event key drops that rule and keeps the rest")
	void unknownEventIsReported() {
		CosmeticSettings.Result result = parse("""
				{"cosmetics":{"pseudohat:hat":{"events":{
					"onTeleport":"animation.hat.pop","onSneak":"animation.hat.sneak"}}}}
				""");

		CosmeticSettings settings = only(result);
		assertEquals(1, settings.events().size());
		assertEquals(CosmeticEvent.ON_SNEAK, settings.events().get(0).event());
		assertFalse(result.problems().isEmpty());
	}

	@Test
	@DisplayName("an unknown type skips the entry instead of guessing a renderer")
	void unknownTypeSkipsEntry() {
		CosmeticSettings.Result result = parse("{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geko\"}}}");

		assertTrue(result.cosmetics().isEmpty());
		assertTrue(result.declared());
		assertFalse(result.problems().isEmpty());
	}

	@Test
	@DisplayName("the default animation is kept as written")
	void defaultAnimation() {
		CosmeticSettings settings = only(parse(
				"{\"cosmetics\":{\"pseudohat:hat\":{\"type\":\"geckolib\",\"default\":\"animation.hat.idle\"}}}"
		));

		assertEquals("animation.hat.idle", settings.defaultAnimation());
	}

	@Test
	@DisplayName("an entry that is not an object is skipped while its neighbours still load")
	void nonObjectEntrySkipped() {
		CosmeticSettings.Result result = parse("{\"cosmetics\":{\"pseudohat:hat\":42,\"pseudohat:poppy\":{}}}");

		assertEquals(1, result.cosmetics().size());
		assertTrue(result.cosmetics().containsKey("pseudohat:poppy"));
		assertFalse(result.problems().isEmpty());
	}

	@Test
	@DisplayName("a settings file may be any namespace, not just the mod's own")
	void foreignNamespaceAllowed() {
		CosmeticSettings settings = only(parse("{\"cosmetics\":{\"otherpack:coolhat\":{\"type\":\"geckolib\"}}}"));

		assertEquals(Identifier.parse("otherpack:coolhat"), settings.geo());
		assertEquals(
				Identifier.parse("otherpack:textures/entity/coolhat.png"),
				settings.texture()
		);
	}

	@Test
	@DisplayName("a cosmetic with no settings file draws as the plain item it always did")
	void defaultsForUndeclaredCosmetic() {
		Identifier id = Identifier.parse("pseudohat:poppy");
		CosmeticSettings settings = CosmeticSettingsIndex.defaultsFor(id);

		assertEquals(CosmeticSettings.Type.ITEM, settings.type());
		assertEquals(id, settings.itemModel());
		assertFalse(settings.glow());
		assertTrue(settings.events().isEmpty());
		assertEquals(1.0F, settings.scale());
	}

	@Test
	@DisplayName("event keys resolve the same whatever case or separators the file used")
	void eventKeyLookupIsForgiving() {
		assertEquals(CosmeticEvent.ON_SNEAK, CosmeticEvent.fromKey("onSneak"));
		assertEquals(CosmeticEvent.ON_SNEAK, CosmeticEvent.fromKey("ON_SNEAK"));
		assertEquals(CosmeticEvent.ON_SNEAK, CosmeticEvent.fromKey("on_sneak"));
		assertEquals(CosmeticEvent.ON_FALL_FLYING, CosmeticEvent.fromKey("onFallFlying"));
		assertNull(CosmeticEvent.fromKey("onTeleport"));
	}
}
