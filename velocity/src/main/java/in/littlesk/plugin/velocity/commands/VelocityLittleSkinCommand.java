package in.littlesk.plugin.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class VelocityLittleSkinCommand implements SimpleCommand {
    private final Core core;
    private final SkinsRestorerBridge skins;

    public VelocityLittleSkinCommand(Core core, SkinsRestorerBridge skins) {
        this.core = core;
        this.skins = skins;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("This command can only be used by a player."));
            return;
        }
        if (invocation.arguments().length != 1 || !invocation.arguments()[0].equalsIgnoreCase("update")) {
            invocation.source().sendMessage(Component.text("Usage: /littleskin update"));
            return;
        }

        Player player = (Player) invocation.source();
        player.sendMessage(Component.text("Fetching your LittleSkin skin..."));
        SkinsRestorerBridge.Request request = this.skins.begin(player.getUniqueId());
        this.skins.findManual(this.core, request, player.getUsername()).orTimeout(10, TimeUnit.SECONDS).whenComplete((profile, error) -> {
            if (!player.isActive() || !this.skins.isCurrent(request)) return;
            if (error != null || !profile.isPresent() || !this.skins.applySkin(request, player, Player.class, profile.get())) {
                player.sendMessage(Component.text("LittleSkin is unavailable or this player is not eligible. No skin change was requested."));
                return;
            }
            player.sendMessage(Component.text("Your LittleSkin skin was submitted to SkinsRestorer."));
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return invocation.arguments().length == 0 ? java.util.Collections.singletonList("update") : java.util.Collections.emptyList();
    }
}
