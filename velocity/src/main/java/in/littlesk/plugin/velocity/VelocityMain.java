package in.littlesk.plugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.logger.Slf4jLogger;
import in.littlesk.plugin.core.Messages;
import in.littlesk.plugin.core.skinsrestorer.SkinsRestorerBridge;
import in.littlesk.plugin.core.skinsrestorer.CompatibilityPolicy;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.plugin.Dependency;
import in.littlesk.plugin.velocity.listeners.VelocityListener;
import in.littlesk.plugin.velocity.commands.VelocityLittleSkinCommand;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

@Plugin(
        id = "getskinfrom-littleskins",
        name = "GetSkinFrom-LitteSkins",
        version = "1.2.2",
        dependencies = { @Dependency(id = "skinsrestorer"), @Dependency(id = "floodgate", optional = true) }
)
public class VelocityMain {

    private final ProxyServer server;
    private final Slf4jLogger logger;
    private final Metrics.Factory metricsFactory;
    @Inject
    @com.velocitypowered.api.plugin.annotation.DataDirectory
    private java.nio.file.Path dataDirectory;
    private Core core;
    private SkinsRestorerBridge skins;

    @Inject
    public VelocityMain(ProxyServer server, Logger logger, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = new Slf4jLogger(logger);
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        if (this.server.getConfiguration().isOnlineMode()) {
            this.logger.warning(Messages.ONLINE_MODE_MESSAGE);
            return;
        }

        String serverImpl = this.server.getVersion().getName();
        String serverVer = this.server.getVersion().getVersion();
        if (!(serverVer.startsWith("3.") || serverVer.startsWith("4."))) {
            this.logger.error(Messages.INCOMPATIBLE_SERVER_MESSAGE_VELOCITY);
            this.server.shutdown();
            return;
        }

        try {
            java.nio.file.Path srConfig = this.dataDirectory.resolveSibling("skinsrestorer").resolve("config.yml");
            this.skins = new SkinsRestorerBridge(this.logger,
                    () -> CompatibilityPolicy.automaticAllowed(srConfig, this.logger),
                    uuid -> CompatibilityPolicy.isBedrock(this.server.getPluginManager().getPlugin("floodgate")
                            .flatMap(container -> container.getInstance()).orElse(null), uuid, this.logger));
            this.skins.watch(this, Player.class, Player::getUniqueId);
        } catch (RuntimeException | LinkageError e) {
            this.logger.error("SkinsRestorer 15.0.0+ API is required.", e);
            return;
        }
        Metrics metrics = this.metricsFactory.make(this, 29008);
        this.core = new Core(this.logger, serverImpl, serverVer);
        server.getEventManager().register(this, new VelocityListener(this.core, this.skins));
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("littleskin").build(),
                new VelocityLittleSkinCommand(this.core, this.skins)
        );
        this.logger.info("GetSkinFrom-LitteSkins enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.skins != null) this.skins.close();
        if (this.core != null) {
            this.core.shutdown();
        }
    }
}
