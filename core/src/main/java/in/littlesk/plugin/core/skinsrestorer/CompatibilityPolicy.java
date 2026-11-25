package in.littlesk.plugin.core.skinsrestorer;

import in.littlesk.plugin.core.logger.PlatformLogger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public final class CompatibilityPolicy {
    private CompatibilityPolicy() { }

    /** Read only. Missing or malformed configuration must never enable an automatic skin. */
    public static boolean automaticAllowed(Path config, PlatformLogger logger) {
        try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            if (!(root instanceof Map)) return false;
            Object advanced = ((Map<?, ?>) root).get("advanced");
            if (advanced == null) return true; // SR's documented default is false.
            if (!(advanced instanceof Map)) return false;
            Object disabled = ((Map<?, ?>) advanced).get("disableOnJoinSkins");
            return disabled == null || Boolean.FALSE.equals(disabled);
        } catch (Exception | LinkageError e) {
            logger.warning("Cannot read SkinsRestorer login policy; skipping automatic LittleSkin fallback: " + e.getMessage());
            return false;
        }
    }

    /** Use the installed Floodgate plugin's class loader, not our own isolated loader. */
    public static boolean isBedrock(Object floodgatePlugin, UUID uuid, PlatformLogger logger) {
        if (floodgatePlugin == null) return false;
        try {
            Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true,
                    floodgatePlugin.getClass().getClassLoader());
            Object api = type.getMethod("getInstance").invoke(null);
            return (Boolean) type.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("Floodgate is installed but its player check failed; skipping LittleSkin: " + e.getMessage());
            return true;
        }
    }
}
