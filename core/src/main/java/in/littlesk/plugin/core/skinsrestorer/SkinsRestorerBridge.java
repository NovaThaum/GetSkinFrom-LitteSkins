package in.littlesk.plugin.core.skinsrestorer;

import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.logger.PlatformLogger;
import in.littlesk.plugin.core.yggdrasil.RemoteGameProfile;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.event.SkinApplyEvent;
import net.skinsrestorer.api.property.SkinProperty;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Never writes LittleSkin textures to SR storage. All applications need a current request. */
public final class SkinsRestorerBridge {
    private final SkinsRestorer api;
    private final PlatformLogger logger;
    private final BooleanSupplier automaticAllowed;
    private final Predicate<UUID> bedrock;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Set<UUID> newerIntent = new HashSet<>();
    private final List<Submission> submissions = new ArrayList<>();
    private boolean closed;

    public SkinsRestorerBridge(PlatformLogger logger, BooleanSupplier automaticAllowed, Predicate<UUID> bedrock) {
        this(SkinsRestorerProvider.get(), logger, automaticAllowed, bedrock);
    }

    SkinsRestorerBridge(SkinsRestorer api, PlatformLogger logger, BooleanSupplier automaticAllowed, Predicate<UUID> bedrock) {
        this.api = api;
        this.logger = logger;
        this.automaticAllowed = automaticAllowed;
        this.bedrock = bedrock;
    }

    public <P> void watch(Object plugin, Class<P> playerType, Function<P, UUID> uuid) {
        this.api.getEventBus().subscribe(plugin, SkinApplyEvent.class,
                event -> onSkinApply(uuid.apply(event.getPlayer(playerType)), event));
    }

    synchronized void onSkinApply(UUID uuid, SkinApplyEvent event) {
        SkinProperty property = event.getProperty();
        Iterator<Submission> iterator = submissions.iterator();
        while (iterator.hasNext()) {
            Submission submission = iterator.next();
            SkinProperty submitted = submission.property.get();
            if (submitted == null) {
                iterator.remove();
            } else if (submitted == property && submission.request.uuid.equals(uuid)) {
                iterator.remove();
                if (!isCurrent(submission.request)) event.setCancelled(true);
                return;
            }
        }
        // Also invalidate on a cancelled external event: it represents a newer intent.
        invalidate(uuid);
    }

    public synchronized Request begin(UUID uuid) {
        Request request = new Request(uuid);
        if (!closed) requests.put(uuid, request);
        return request;
    }

    /** Do not start a late login fallback after SR has already accepted a skin event. */
    public synchronized Request beginAutomatic(UUID uuid) {
        Request request = new Request(uuid);
        if (!closed && !newerIntent.contains(uuid) && !requests.containsKey(uuid)) requests.put(uuid, request);
        return request;
    }

    public synchronized boolean isCurrent(Request request) {
        return !closed && requests.get(request.uuid) == request;
    }

    public synchronized void invalidate(UUID uuid) {
        requests.remove(uuid);
        newerIntent.add(uuid);
    }

    public synchronized void disconnect(UUID uuid) {
        requests.remove(uuid);
        newerIntent.remove(uuid);
    }

    public synchronized void close() {
        closed = true;
        requests.clear();
        newerIntent.clear();
        // Keep weak submission markers so already queued SR events can still be cancelled.
    }

    private boolean eligible(Request request, boolean automatic) {
        return isCurrent(request) && !bedrock.test(request.uuid)
                && (!automatic || automaticAllowed.getAsBoolean());
    }

    /** SR resolution and policy/file reads run off the server thread. */
    public CompletableFuture<Optional<RemoteGameProfile>> findFallback(Core core, Request request, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return eligible(request, true)
                        && !api.getPlayerStorage().getSkinForPlayer(request.uuid, name, false).isPresent();
            } catch (Exception | LinkageError e) {
                logger.error("Could not resolve SkinsRestorer login skin for " + name + "; skipping LittleSkin fallback.", e);
                return false;
            }
        }).thenCompose(allow -> allow ? core.getGameProfileByName(name)
                : CompletableFuture.completedFuture(Optional.<RemoteGameProfile>empty()))
                .thenApply(profile -> eligible(request, true) ? profile : Optional.empty());
    }

    public CompletableFuture<Optional<RemoteGameProfile>> findManual(Core core, Request request, String name) {
        return CompletableFuture.supplyAsync(() -> eligible(request, false))
                .thenCompose(allow -> allow ? core.getGameProfileByName(name)
                        : CompletableFuture.completedFuture(Optional.<RemoteGameProfile>empty()))
                .thenApply(profile -> eligible(request, false) ? profile : Optional.empty());
    }

    public synchronized <P> boolean applySkin(Request request, P player, Class<P> playerType, RemoteGameProfile profile) {
        if (!isCurrent(request)) return false;
        Optional<RemoteGameProfile.Property> texture = profile.getProperty("textures");
        if (!texture.isPresent() || texture.get().value == null || texture.get().value.isEmpty()
                || texture.get().signature == null || texture.get().signature.isEmpty()) return false;
        SkinProperty property = SkinProperty.of(texture.get().value, texture.get().signature);
        Submission submission = new Submission(request, property);
        submissions.removeIf(entry -> entry.property.get() == null);
        submissions.add(submission);
        try {
            api.getSkinApplier(playerType).applySkin(player, property);
            // SR can schedule the event later; onSkinApply checks this request again.
            return true;
        } catch (RuntimeException | LinkageError e) {
            submissions.remove(submission);
            logger.error("SkinsRestorer rejected a LittleSkin skin application.", e);
            return false;
        }
    }

    public static final class Request {
        private final UUID uuid;
        private Request(UUID uuid) { this.uuid = uuid; }
    }

    private static final class Submission {
        private final Request request;
        private final WeakReference<SkinProperty> property;
        private Submission(Request request, SkinProperty property) {
            this.request = request;
            this.property = new WeakReference<>(property);
        }
    }
}
