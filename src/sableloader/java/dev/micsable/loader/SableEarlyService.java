package dev.micsable.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

/**
 * The early half of Make it Compatible's single-jar loader: seeds (and cleans up) the loader-level
 * {@code config/fml.toml} dependency overrides that a patch needs before mods are sorted, so the jar works with
 * <b>zero manual setup</b>.
 *
 * <p>Today it carries one entry, for the LittleTiles &times; Sable patch. Sable's {@code neoforge.mods.toml}
 * declares LittleTiles {@code incompatible}, and NeoForge enforces that in its loader ({@code ModSorter})
 * <em>before any mod runs</em>, so a plain mod jar cannot lift it. NeoForge does honour a first-party escape
 * hatch in {@code config/fml.toml}:</p>
 *
 * <pre>{@code [dependencyOverrides]
 * sable = ["-littletiles"]}</pre>
 *
 * <p>which drops Sable's {@code littletiles} constraint before it is checked. This class is an
 * {@link ITransformationService}: NeoForge's {@code ModDirTransformerDiscoverer} finds any {@code mods/} jar that
 * provides this service and loads it into the early <em>service</em> layer, and ModLauncher calls every service's
 * {@link #onLoad} <em>before</em> {@code FMLServiceProvider.initialize()} (where {@code FMLConfig.load()} reads the
 * overrides). So writing the override here lands it in time for the very same launch - no second start needed.</p>
 *
 * <p><b>Auto-detect.</b> The override is only written when <em>both</em> {@code littletiles} and {@code sable} are
 * actually installed (enabled {@code .jar}s in {@code mods/}). If either is missing, any override this loader
 * previously wrote is removed again - otherwise NeoForge shows a "Unknown mod littletiles referenced in dependency
 * overrides for mod sable" warning when the target isn't there. Presence is detected by reading each mod jar's
 * {@code neoforge.mods.toml}/{@code mods.toml} and matching {@code modId} <em>only inside its {@code [[mods]]}
 * blocks</em> - never dependency declarations, so Sable's own {@code incompatible littletiles} line doesn't count
 * as LittleTiles being present.</p>
 *
 * <p>Because a service-layer jar is excluded from normal mod scanning, the actual mod ships <em>nested inside</em>
 * this same jar and is injected back into mod discovery by {@link NestedModLocator} - one file, no fml.toml edit,
 * no separate bootstrap jar.</p>
 */
public final class SableEarlyService implements ITransformationService {

    private static final String MARKER = "-littletiles";
    private static final String SECTION = "[dependencyOverrides]";
    private static final String ENTRY = "sable = [\"-littletiles\"]";

    /** The two mods whose mutual incompatibility the override lifts; both must be present for it to apply. */
    private static final String MOD_SABLE = "sable";
    private static final String MOD_LITTLETILES = "littletiles";

    /** Matches a {@code modId = "..."} / {@code modId="..."} assignment (single or double quoted, any spacing). */
    private static final Pattern MODID = Pattern.compile("^modId\\s*=\\s*[\"']([^\"']+)[\"']");

    @Override
    public String name() {
        return "micsable_loader";
    }

    @Override
    public void onLoad(final IEnvironment env, final Set<String> otherServices) {
        try {
            final Path gameDir = env.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElseGet(() -> Paths.get("."));
            applyOverrides(gameDir);
        } catch (final Throwable t) {
            // Never break launch over this. Worst case the user adds/removes the line(s) by hand (README documents them).
            System.err.println("[make-it-compatible: sable loader] could not update the fml.toml dependency override: " + t);
        }
    }

    /** Add every override whose target mods are present, and remove any whose targets aren't. */
    private static void applyOverrides(final Path gameDir) throws IOException {
        final Path modsDir = gameDir.resolve("mods");
        final Path fmlToml = gameDir.resolve("config").resolve("fml.toml");

        // LittleTiles x Sable: the override only makes sense - and only avoids a warning - when BOTH are installed.
        final boolean apply = isModPresent(modsDir, MOD_LITTLETILES) && isModPresent(modsDir, MOD_SABLE);
        if (apply)
            ensureEntry(fmlToml, MARKER, ENTRY, "Sable<->LittleTiles");
        else
            removeEntry(fmlToml, MARKER, ENTRY, "Sable<->LittleTiles");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mod presence detection
    // ---------------------------------------------------------------------------------------------------------------

    /** @return true if an enabled {@code .jar} in {@code modsDir} declares a mod with id {@code modId}. */
    private static boolean isModPresent(final Path modsDir, final String modId) {
        if (!Files.isDirectory(modsDir))
            return false;
        try (Stream<Path> files = Files.list(modsDir)) {
            return files
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .anyMatch(p -> jarProvidesMod(p, modId));
        } catch (final IOException e) {
            return false;
        }
    }

    /** @return true if the jar's mods metadata declares {@code modId} in a {@code [[mods]]} block (not a dependency). */
    private static boolean jarProvidesMod(final Path jar, final String modId) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            for (final String tomlPath : new String[] {"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                final ZipEntry entry = zf.getEntry(tomlPath);
                if (entry == null)
                    continue;
                try (InputStream in = zf.getInputStream(entry)) {
                    final String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (declaresModId(toml, modId))
                        return true;
                }
            }
        } catch (final Throwable ignored) {
            // Not a readable zip, no mods.toml, or an I/O hiccup - treat as "doesn't provide the mod".
        }
        return false;
    }

    /**
     * @return true if {@code toml} declares {@code modId} as one of its own mods. Only {@code modId} keys inside a
     *         {@code [[mods]]} array-of-tables count; keys under {@code [[dependencies.*]]} (e.g. Sable's own
     *         {@code incompatible littletiles} entry) are ignored, so a dependency reference is never mistaken for
     *         the mod being installed.
     */
    private static boolean declaresModId(final String toml, final String modId) {
        boolean inModsBlock = false;
        for (final String raw : toml.split("\n")) {
            final String line = raw.strip();
            if (line.startsWith("[")) {
                // A new table / array-of-tables header ends any [[mods]] block and may start a new one.
                inModsBlock = line.startsWith("[[mods]]");
                continue;
            }
            if (inModsBlock) {
                final Matcher m = MODID.matcher(line);
                if (m.find() && m.group(1).equals(modId))
                    return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // fml.toml editing (idempotent; touches as little as possible)
    // ---------------------------------------------------------------------------------------------------------------

    /** Ensure {@code config/fml.toml} contains a single dependency-override {@code entry} (idempotent via {@code marker}). */
    private static void ensureEntry(final Path fmlToml, final String marker, final String entry, final String label) throws IOException {
        if (Files.exists(fmlToml)) {
            final String content = new String(Files.readAllBytes(fmlToml), StandardCharsets.UTF_8);
            if (content.contains(marker))
                return; // already softened (any form) - leave the file untouched
            write(fmlToml, inject(content, entry));
            System.out.println("[make-it-compatible: sable loader] added " + label + " dependency override to " + fmlToml);
        } else {
            Files.createDirectories(fmlToml.getParent());
            write(fmlToml, SECTION + nl() + entry + nl());
            System.out.println("[make-it-compatible: sable loader] created " + fmlToml + " with " + label + " dependency override");
        }
    }

    /** Remove the dependency-override {@code entry} we may have written earlier (idempotent; no-op if absent). */
    private static void removeEntry(final Path fmlToml, final String marker, final String entry, final String label) throws IOException {
        if (!Files.exists(fmlToml))
            return;
        final String content = new String(Files.readAllBytes(fmlToml), StandardCharsets.UTF_8);
        if (!content.contains(marker))
            return; // nothing of ours to remove
        final String updated = strip(content, entry);
        if (!updated.equals(content)) {
            write(fmlToml, updated);
            System.out.println("[make-it-compatible: sable loader] removed " + label
                + " dependency override from " + fmlToml + " (target mods not both installed)");
        }
    }

    /** Insert one override entry into an existing fml.toml, touching as little as possible. */
    private static String inject(final String content, final String entry) {
        // Default FML file ships an empty inline table: dependencyOverrides = {}
        if (content.contains("dependencyOverrides = {}"))
            return content.replace("dependencyOverrides = {}", "dependencyOverrides = {" + entry + "}");
        // An explicit [dependencyOverrides] table already exists -> add our line right under the header.
        final int header = content.indexOf(SECTION);
        if (header >= 0) {
            int after = content.indexOf('\n', header);
            after = after < 0 ? content.length() : after + 1;
            return content.substring(0, after) + entry + nl() + content.substring(after);
        }
        // A populated inline table without our key -> add it as the first key.
        final int inline = content.indexOf("dependencyOverrides = {");
        if (inline >= 0) {
            final int brace = content.indexOf('{', inline) + 1;
            return content.substring(0, brace) + entry + ", " + content.substring(brace);
        }
        // No dependencyOverrides key at all -> append the table.
        return content + nl() + SECTION + nl() + entry + nl();
    }

    /** Reverse of {@link #inject}: strip the entry from either an inline table or a {@code [dependencyOverrides]} line. */
    private static String strip(final String content, final String entry) {
        String out = content;
        // Inline-table forms first (most specific), covering every position of our key.
        out = out.replace("{" + entry + ", ", "{"); // first key of several
        out = out.replace(", " + entry, "");         // a later key
        out = out.replace("{" + entry + "}", "{}");  // the sole key
        // Section/line form: our entry sits on its own line - drop the whole line, including its line break.
        out = out.replaceAll("(?m)^[\\t ]*" + Pattern.quote(entry) + "[\\t ]*\\r?\\n?", "");
        return out;
    }

    private static void write(final Path file, final String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String nl() {
        return System.lineSeparator();
    }

    @Override
    public void initialize(final IEnvironment environment) {
        // nothing - all work happens in onLoad, which runs before FMLConfig is read
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of(); // we transform no classes; we only seed config lines
    }
}
