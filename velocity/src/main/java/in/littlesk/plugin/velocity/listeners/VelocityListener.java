package in.littlesk.plugin.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;

public final class VelocityListener {
    private final Core core;
    private final SkinsRestorerBridge skins;

    public VelocityListener(Core core, SkinsRestorerBridge skins) {
        this.core = core;
        this.skins = skins;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        // Wait for the backend connection so SR can send its skin refresh message.
        // Server switches must not reapply LittleSkin over a later /skin selection.
        if (event.getPreviousServer() != null || hasTextures(player)) {
            return;
        }
        SkinsRestorerBridge.Request request = this.skins.beginAutomatic(player.getUniqueId());
        this.skins.findFallback(this.core, request, player.getUsername()).whenComplete((profile, error) -> {
            if (error == null && profile.isPresent() && player.isActive() && !hasTextures(player)) {
                this.skins.applySkin(request, player, Player.class, profile.get());
            }
        });
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommand(CommandExecuteEvent event) {
        if (event.getCommandSource() instanceof Player) {
            this.skins.invalidate(((Player) event.getCommandSource()).getUniqueId());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.skins.disconnect(event.getPlayer().getUniqueId());
    }

    private static boolean hasTextures(Player player) {
        return player.getGameProfileProperties().stream()
                .anyMatch(property -> "textures".equals(property.getName()));
    }
}
