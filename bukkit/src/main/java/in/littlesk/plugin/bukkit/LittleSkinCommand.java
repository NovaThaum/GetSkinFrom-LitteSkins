package in.littlesk.plugin.bukkit;

import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;
import in.littlesk.plugin.core.yggdrasil.RemoteGameProfile;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LittleSkinCommand implements CommandExecutor {
    private final BukkitMain plugin;
    private final Core core;
    private final SkinsRestorerBridge skins;

    public LittleSkinCommand(BukkitMain plugin, Core core, SkinsRestorerBridge skins) {
        this.plugin = plugin;
        this.core = core;
        this.skins = skins;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("update")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /littleskin update");
            return true;
        }

        Player player = (Player) sender;
        player.sendMessage(ChatColor.GRAY + "Fetching your LittleSkin skin...");
        SkinsRestorerBridge.Request request = this.skins.begin(player.getUniqueId());
        this.skins.findManual(this.core, request, player.getName()).whenComplete((profile, error) ->
                this.plugin.runForPlayer(player, () -> {
                    if (!this.skins.isCurrent(request)) return;
                    if (error != null || !profile.isPresent() || !this.skins.applySkin(request, player, Player.class, profile.get())) {
                        player.sendMessage(ChatColor.RED + "LittleSkin is unavailable or this player is not eligible. No skin change was requested.");
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "Your LittleSkin skin was submitted to SkinsRestorer.");
                }));
        return true;
    }
}
