package in.littlesk.plugin.bukkit;

import in.littlesk.plugin.bukkit.listeners.PaperListener;
import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.logger.JulLogger;
import in.littlesk.plugin.core.Messages;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;
import in.littlesk.plugin.core.skinsrestorer.CompatibilityPolicy;
import org.bukkit.entity.Player;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitMain extends JavaPlugin {

    private Server server;
    private JulLogger logger;
    private Core core;
    private SkinsRestorerBridge skins;

    @Override
    public void onEnable() {
        this.server = this.getServer();
        this.logger = new JulLogger(this.getLogger());

        PluginManager pm = this.getServer().getPluginManager();

        if (Bukkit.getOnlineMode()) {
            this.logger.warning(Messages.ONLINE_MODE_MESSAGE);
            pm.disablePlugin(this);
            return;
        }

        if (checkIsPaper()) {
            try {
                java.nio.file.Path srConfig = pm.getPlugin("SkinsRestorer").getDataFolder().toPath().resolve("config.yml");
                this.skins = new SkinsRestorerBridge(this.logger,
                        () -> CompatibilityPolicy.automaticAllowed(srConfig, this.logger),
                        uuid -> CompatibilityPolicy.isBedrock(pm.getPlugin("floodgate"), uuid, this.logger));
                this.skins.watch(this, Player.class, Player::getUniqueId);
            } catch (RuntimeException | LinkageError e) {
                this.logger.error("SkinsRestorer 15.0.0+ API is required.", e);
                pm.disablePlugin(this);
                return;
            }
            this.core = new Core(this.logger, this.server.getName(), this.server.getVersion());
            pm.registerEvents(new PaperListener(this, this.core, this.skins), this);
            if (this.getCommand("littleskin") != null) {
                this.getCommand("littleskin").setExecutor(new LittleSkinCommand(this, this.core, this.skins));
            }
        } else {
            this.logger.error(Messages.INCOMPATIBLE_SERVER_MESSAGE_BUKKIT);
            this.logger.warning(Messages.INCOMPATIBLE_SERVER_STAY_TUNED_MESSAGE);
            this.getServer().shutdown();
            return;
        }

        Metrics metrics = new Metrics(this, 29005);
        this.logger.info("GetSkinFrom-LitteSkins enabled.");
    }

    @Override
    public void onDisable() {
        this.logger.warning(Messages.DISABLE_MESSAGE);
        if (this.skins != null) this.skins.close();
        if (this.core != null) {
            this.core.shutdown();
        }
    }

    private static boolean checkIsPaper() {
        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Keep async HTTP callbacks on the player's scheduler, including on Folia. */
    public void runForPlayer(Player player, Runnable task) {
        if (!this.isEnabled() || !player.isOnline()) {
            return;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        } catch (ClassNotFoundException e) {
            this.server.getScheduler().runTask(this, () -> {
                if (player.isOnline()) task.run();
            });
            return;
        }
        try {
            java.lang.reflect.Method getter = org.bukkit.entity.Entity.class.getMethod("getScheduler");
            Object scheduler = getter.invoke(player);
            getter.getReturnType().getMethod("run", org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class, Runnable.class)
                    .invoke(scheduler, this, (java.util.function.Consumer<Object>) ignored -> task.run(), null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.logger.error("Could not schedule LittleSkin update for player.", e);
        }
    }
}
