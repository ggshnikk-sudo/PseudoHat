package dev.pseudohat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dev resource pack under {@code run/} is what the in-game check renders, and nothing validates it
 * until GeckoLib logs a missing animation at runtime. These tests read those exact files and pin the
 * cross-references down: an animation named in the config must exist in the animation file, its bones
 * must exist in the geometry, and the texture the defaults point at must be on disk.
 *
 * <p>The pack is a dev fixture, not a build input, so the whole class is skipped where it is absent.
 */
class SamplePackTest {
	private static final Path PACK = Path.of("run/resourcepacks/pseudohat-sample/assets/pseudohat");
	private static final Path GEO = PACK.resolve("geckolib/models/hat.geo.json");
	private static final Path ANIMATIONS = PACK.resolve("geckolib/animations/hat.animation.json");
	private static final Path CONFIG = PACK.resolve("config/models/hat.json");
	private static final Path HUINA_GEO = PACK.resolve("geckolib/models/huina.geo.json");
	private static final Path HUINA_ANIMATIONS = PACK.resolve("geckolib/animations/huina.animation.json");
	private static final Path HUINA_CONFIG = PACK.resolve("config/models/huina.json");

	@BeforeAll
	static void requireSamplePack() {
		Assumptions.assumeTrue(Files.isDirectory(PACK), () -> "sample pack not present at " + PACK);
	}

	@Test
	@DisplayName("the config's default and event animations all exist in the animation file")
	void animationNamesResolve() throws IOException {
		CosmeticSettings settings = sampleSettings();
		Set<String> declared = animationNames(read(ANIMATIONS));

		for (String referenced : referencedAnimations(settings)) {
			assertTrue(declared.contains(referenced),
					() -> referenced + " is referenced by the config but the animation file declares " + declared);
		}
	}

	@Test
	@DisplayName("every bone an animation moves exists in the geometry")
	void animationBonesExist() throws IOException {
		Set<String> bones = geometryBones(read(GEO));
		JsonObject animations = read(ANIMATIONS).getAsJsonObject("animations");

		for (String name : animations.keySet()) {
			JsonObject bonesObject = animations.getAsJsonObject(name).getAsJsonObject("bones");

			if (bonesObject == null) {
				continue;
			}

			for (String bone : bonesObject.keySet()) {
				assertTrue(bones.contains(bone), () -> name + " moves bone '" + bone + "', geometry has " + bones);
			}
		}
	}

	@Test
	@DisplayName("the geo defaults resolve to files that exist, including the glowmask")
	void referencedFilesExist() throws IOException {
		CosmeticSettings settings = sampleSettings();

		assertEquals(Identifier.fromNamespaceAndPath("pseudohat", "hat"), settings.geo());
		assertEquals(Identifier.fromNamespaceAndPath("pseudohat", "hat"), settings.animations());
		assertTrue(Files.isRegularFile(GEO), () -> GEO + " is missing");
		assertTrue(Files.isRegularFile(ANIMATIONS), () -> ANIMATIONS + " is missing");

		Identifier texture = settings.texture();
		Path textureFile = PACK.resolve(texture.getPath());
		assertTrue(Files.isRegularFile(textureFile), () -> textureFile + " is missing");

		assertTrue(settings.glow(), "the sample is meant to exercise the glow pass");

		Path glowmask = textureFile.resolveSibling(
				textureFile.getFileName().toString().replace(".png", "_glowmask.png"));
		assertTrue(Files.isRegularFile(glowmask), () -> glowmask + " is missing");
	}

	@Test
	@DisplayName("huina: config animation exists, spins an existing bone, texture on disk")
	void huinaCrossReferences() throws IOException {
		CosmeticSettings settings = huinaSettings();
		JsonObject animations = read(HUINA_ANIMATIONS).getAsJsonObject("animations");
		Set<String> bones = geometryBones(read(HUINA_GEO));

		for (String referenced : referencedAnimations(settings)) {
			assertTrue(animations.has(referenced),
					() -> referenced + " is referenced by the huina config but the animation file declares "
							+ animations.keySet());
		}

		for (String name : animations.keySet()) {
			JsonObject bonesObject = animations.getAsJsonObject(name).getAsJsonObject("bones");

			if (bonesObject == null) {
				continue;
			}

			for (String bone : bonesObject.keySet()) {
				assertTrue(bones.contains(bone), () -> name + " moves bone '" + bone + "', huina geometry has " + bones);
			}
		}

		assertEquals(Identifier.fromNamespaceAndPath("pseudohat", "huina"), settings.geo());
		assertEquals(Identifier.fromNamespaceAndPath("pseudohat", "huina"), settings.animations());
		assertTrue(Files.isRegularFile(HUINA_GEO), () -> HUINA_GEO + " is missing");
		assertTrue(Files.isRegularFile(HUINA_ANIMATIONS), () -> HUINA_ANIMATIONS + " is missing");

		Path textureFile = PACK.resolve(settings.texture().getPath());
		assertTrue(Files.isRegularFile(textureFile), () -> textureFile + " is missing");
	}

	@Test
	@DisplayName("huina: the Blockbench display export places the head with the intended transform")
	void huinaDisplayTransform() throws IOException {
		Path displayFile = PACK.resolve("models/item/huina.json");
		HeadDisplay.Transform transform = HeadDisplay.parse(read(displayFile));

		assertTrue(transform != null && !transform.isNone(), () -> displayFile + " must yield a display transform");
		assertEquals(new CosmeticSettings.Vec3(0.0F, 10.0F, 0.0F), transform.offset());
		assertEquals(CosmeticSettings.Vec3.ZERO, transform.rotation());
		assertEquals(0.72461F, transform.scale(), 0.0001F);
	}

	@Test
	@DisplayName("an element model like hat.json never supplies the display transform")
	void elementModelHasNoDisplayTransform() throws IOException {
		Path hatFile = PACK.resolve("models/item/hat.json");
		assertEquals(null, HeadDisplay.parse(read(hatFile)),
				() -> hatFile + " is a real element model, its config transforms must stay authoritative");
	}

	private static CosmeticSettings huinaSettings() throws IOException {
		CosmeticSettings.Result parsed = CosmeticSettings.parse(
				Identifier.fromNamespaceAndPath("pseudohat", "config/models/huina.json"), read(HUINA_CONFIG));

		assertEquals(List.of(), parsed.problems(), "the huina config must parse without problems");
		assertEquals(1, parsed.cosmetics().size(), () -> "expected one entry, got " + parsed.cosmetics().keySet());

		CosmeticSettings settings = parsed.cosmetics().values().iterator().next();

		assertEquals(CosmeticSettings.Type.GECKOLIB, settings.type());
		return settings;
	}

	private static CosmeticSettings sampleSettings() throws IOException {
		CosmeticSettings.Result parsed = CosmeticSettings.parse(
				Identifier.fromNamespaceAndPath("pseudohat", "config/models/hat.json"), read(CONFIG));

		assertEquals(List.of(), parsed.problems(), "the sample config must parse without problems");
		assertEquals(1, parsed.cosmetics().size(), () -> "expected one entry, got " + parsed.cosmetics().keySet());

		CosmeticSettings settings = parsed.cosmetics().values().iterator().next();

		assertEquals(CosmeticSettings.Type.GECKOLIB, settings.type());
		return settings;
	}

	private static Set<String> referencedAnimations(CosmeticSettings settings) {
		Set<String> referenced = new HashSet<>();

		if (settings.defaultAnimation() != null) {
			referenced.add(settings.defaultAnimation());
		}

		for (CosmeticSettings.EventRule rule : settings.events()) {
			referenced.add(rule.animation());
		}

		return referenced;
	}

	private static Set<String> animationNames(JsonObject root) {
		return new HashSet<>(root.getAsJsonObject("animations").keySet());
	}

	private static Set<String> geometryBones(JsonObject root) {
		Set<String> bones = new HashSet<>();

		for (JsonElement geometry : root.getAsJsonArray("minecraft:geometry")) {
			for (JsonElement bone : geometry.getAsJsonObject().getAsJsonArray("bones")) {
				bones.add(bone.getAsJsonObject().get("name").getAsString());
			}
		}

		return bones;
	}

	private static JsonObject read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), () -> path + " is missing");

		try (Reader reader = Files.newBufferedReader(path)) {
			return StrictJsonParser.parse(reader).getAsJsonObject();
		}
	}
}
