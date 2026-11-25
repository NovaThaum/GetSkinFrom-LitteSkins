package org.geysermc.floodgate.api;

import java.util.UUID;

/** Test fixture for the optional API's public contract. Never bundled in the plugin. */
public final class FloodgateApi {
    public static FloodgateApi getInstance() { return new FloodgateApi(); }
    public boolean isFloodgatePlayer(UUID uuid) { return uuid.getLeastSignificantBits() == 1; }
}
