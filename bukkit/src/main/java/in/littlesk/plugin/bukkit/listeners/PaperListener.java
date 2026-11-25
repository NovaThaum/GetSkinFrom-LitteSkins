package in.littlesk.plugin.bukkit.listeners;

import in.littlesk.plugin.bukkit.BukkitMain;
import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class PaperListener implements Listener {
    private final BukkitMain plugin;
    private final Core core;
    private final SkinsRestorerBridge skins;

    public PaperListener(BukkitMain plugin, Core core, SkinsRestorerBridge skins) {
        this.plugin = plugin;
        this.core = core;
        this.skins = skins;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hasTextures(player)) {
            return;
        }
        SkinsRestorerBridge.Request request = this.skins.beginAutomatic(player.getUniqueId());
        this.skins.findFallback(this.core, request, player.getName()).whenComplete((profile, error) -> {
            if (error == null && profile.isPresent()) {
                this.plugin.runForPlayer(player, () -> {
                    // A skin may have been selected while the lookup was in flight.
                    if (player.isOnline() && !hasTextures(player)) {
                        this.skins.applySkin(request, player, Player.class, profile.get());
                    }
                });
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // Include aliases and namespaced commands. A new command abandons old HTTP work.
        this.skins.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.skins.disconnect(event.getPlayer().getUniqueId());
    }

    private static boolean hasTextures(Player player) {
        return player.getPlayerProfile().getProperties().stream()
                .anyMatch(property -> "textures".equals(property.getName()));
    }
}
