package in.littlesk.plugin.core.skinsrestorer;

import in.littlesk.plugin.core.logger.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityPolicyTest {
    @TempDir Path dir;
    private final PlatformLogger logger = mock(PlatformLogger.class);

    private Path config(String yaml) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test void disabledPolicyIsRespectedAndChangesAreReread() throws Exception {
        Path file = config("advanced:\n  disableOnJoinSkins: false\n");
        assertTrue(CompatibilityPolicy.automaticAllowed(file, logger));
        config("advanced:\n  disableOnJoinSkins: true\n");
        assertFalse(CompatibilityPolicy.automaticAllowed(file, logger));
    }

    @Test void defaultFalseIsUsedForOmittedSetting() throws Exception {
        assertTrue(CompatibilityPolicy.automaticAllowed(config("advanced: {}\n"), logger));
        assertTrue(CompatibilityPolicy.automaticAllowed(config("storage: {}\n"), logger));
    }

    @Test void missingMalformedAndWrongTypesFailClosed() throws Exception {
        assertFalse(CompatibilityPolicy.automaticAllowed(dir.resolve("missing"), logger));
        for (String yaml : new String[] { "", "[]", "advanced: []", "advanced: [", "advanced:\n  disableOnJoinSkins: 'false'", "!!java.lang.Runtime {}" }) {
            assertFalse(CompatibilityPolicy.automaticAllowed(config(yaml), logger), yaml);
        }
    }

    @Test void installedApiDistinguishesJavaAndBedrockPlayers() {
        assertTrue(CompatibilityPolicy.isBedrock(this, new UUID(0, 1), logger));
        assertFalse(CompatibilityPolicy.isBedrock(this, new UUID(0, 2), logger));
    }

    @Test void noFloodgateIsAllowedButBrokenInstalledApiFailsClosed() {
        assertFalse(CompatibilityPolicy.isBedrock(null, UUID.randomUUID(), logger));
        assertTrue(CompatibilityPolicy.isBedrock(new Object(), UUID.randomUUID(), logger));
    }
}
