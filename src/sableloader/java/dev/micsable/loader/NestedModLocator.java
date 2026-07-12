package dev.micsable.loader;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import cpw.mods.modlauncher.api.IEnvironment;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * The second half of Make it Compatible's single-jar loader: injects the <b>actual mod</b>, which ships nested
 * inside this jar at {@value #INNER_JAR}, back into NeoForge's mod discovery.
 *
 * <p>Why: the distributed jar declares an {@link cpw.mods.modlauncher.api.ITransformationService}
 * ({@link SableEarlyService}, which seeds the Sable&harr;LittleTiles {@code fml.toml} override), and FML
 * loads such jars onto the early <em>service</em> module layer and then skips them during normal mod scanning
 * (their path is already "located"). So the mod half can't live top-level in the same jar. Instead it is nested as
 * a complete inner mod jar, and this {@link IModFileCandidateLocator} - which FML discovers from the same service
 * layer via {@code ServiceLoader} - extracts it to {@code <gameDir>/.micsable/} and adds that path to the
 * discovery pipeline. Net result: one jar in {@code mods/} that both lifts the loader-level incompatibility and
 * carries the mod (the Essential/ServerPackLocator pattern).</p>
 *
 * <p>The nested jar is re-extracted on every launch (a ~100 KB copy, atomic replace), so the extracted copy can
 * never go stale when the jar is updated. In a dev environment the resource is absent (the mod loads from class
 * directories instead) and this locator simply does nothing.</p>
 */
public final class NestedModLocator implements IModFileCandidateLocator {

    /** Where the real mod jar lives inside this jar (see the {@code distJar} task in build.gradle). */
    private static final String INNER_JAR = "/META-INF/inner/make-it-compatible-sable-mod.jar";

    /** Folder under the game dir the inner jar is extracted to (outside {@code mods/}, so nothing double-scans it). */
    private static final String EXTRACT_DIR = ".micsable";

    @Override
    public void findCandidates(final ILaunchContext context, final IDiscoveryPipeline pipeline) {
        try {
            final URL inner = NestedModLocator.class.getResource(INNER_JAR);
            if (inner == null)
                return; // dev environment (mod loads from class dirs) or repacked without the inner jar

            final Path gameDir = context.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseGet(() -> Paths.get("."));
            final Path dir = gameDir.resolve(EXTRACT_DIR);
            Files.createDirectories(dir);
            final Path target = dir.resolve("make-it-compatible-sable-mod.jar");

            // Extract via temp file + atomic move so a crash mid-write can't leave a truncated jar behind.
            final Path tmp = Files.createTempFile(dir, "make-it-compatible-sable-mod", ".tmp");
            try (InputStream in = inner.openStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            pipeline.addPath(List.of(target), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
            System.out.println("[make-it-compatible: sable loader] injected nested mod jar: " + target);
        } catch (final Throwable t) {
            // Don't kill the launch; the game just runs without the mod and the log says why.
            System.err.println("[make-it-compatible: sable loader] failed to extract/inject the nested mod jar: " + t);
        }
    }

    @Override
    public String toString() {
        return "make-it-compatible: sable nested-mod locator";
    }
}
