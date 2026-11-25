package in.littlesk.plugin.core.skinsrestorer;

import in.littlesk.plugin.core.Core;
import in.littlesk.plugin.core.logger.PlatformLogger;
import in.littlesk.plugin.core.yggdrasil.RemoteGameProfile;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.event.SkinApplyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import net.skinsrestorer.api.property.SkinApplier;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinsRestorerBridgeTest {
    private SkinsRestorer api;
    private PlayerStorage storage;
    private PlatformLogger logger;
    private Core core;
    private SkinsRestorerBridge bridge;
    private final AtomicBoolean automatic = new AtomicBoolean(true);
    private final AtomicBoolean bedrock = new AtomicBoolean(false);
    private SkinsRestorerBridge.Request request;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        api = mock(SkinsRestorer.class);
        storage = mock(PlayerStorage.class);
        logger = mock(PlatformLogger.class);
        core = mock(Core.class);
        when(api.getPlayerStorage()).thenReturn(storage);
        bridge = new SkinsRestorerBridge(api, logger, automatic::get, ignored -> bedrock.get());
        request = bridge.begin(uuid);
    }

    @Test
    void preservesSkinsRestorerLoginSkinWithoutQueryingLittleSkin() throws Exception {
        when(storage.getSkinForPlayer(uuid, "player", false))
                .thenReturn(Optional.of(SkinProperty.of("saved-or-premium-or-default", "signature")));
        assertFalse(bridge.findFallback(core, request, "player").get(5, TimeUnit.SECONDS).isPresent());
        verifyNoInteractions(core);
        verify(storage, never()).removeSkinIdOfPlayer(any());
    }

    @Test
    void queriesLittleSkinOnlyWhenSkinsRestorerHasNoSkin() throws Exception {
        RemoteGameProfile profile = profile("value", "signature");
        when(storage.getSkinForPlayer(uuid, "player", false)).thenReturn(Optional.empty());
        when(core.getGameProfileByName("player"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        assertSame(profile, bridge.findFallback(core, request, "player").get(5, TimeUnit.SECONDS).get());
    }

    @Test
    void storageFailureDoesNotFallThroughToAnOverwrite() throws Exception {
        when(storage.getSkinForPlayer(uuid, "player", false)).thenThrow(new IllegalStateException("storage unavailable"));
        assertFalse(bridge.findFallback(core, request, "player").get(5, TimeUnit.SECONDS).isPresent());
        verifyNoInteractions(core);
        verify(logger).error(contains("skipping LittleSkin"), any(Throwable.class));
    }

    @Test
    void appliesSignedTexturesThroughApiWithoutWritingStorage() {
        SkinApplier<Object> applier = mock(SkinApplier.class);
        when(api.getSkinApplier(Object.class)).thenReturn(applier);
        Object player = new Object();
        assertTrue(bridge.applySkin(request, player, Object.class, profile("value", "signature")));
        verify(applier).applySkin(player, SkinProperty.of("value", "signature"));
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnsignedOrEmptyTexturesWithoutCallingApi() {
        assertFalse(bridge.applySkin(request, new Object(), Object.class, profile("value", null)));
        assertFalse(bridge.applySkin(request, new Object(), Object.class, profile("", "signature")));
        verifyNoInteractions(api);
    }

    @Test
    void reportsApiFailure() {
        when(api.getSkinApplier(Object.class)).thenThrow(new IllegalStateException("not ready"));
        assertFalse(bridge.applySkin(request, new Object(), Object.class, profile("value", "signature")));
        verify(logger).error(contains("rejected"), any(Throwable.class));
    }


    @Test
    void disabledLoginPolicySkipsStorageAndLittleSkin() throws Exception {
        automatic.set(false);
        assertFalse(bridge.findFallback(core, request, "player").get(5, TimeUnit.SECONDS).isPresent());
        verifyNoInteractions(storage, core);
    }

    @Test
    void floodgateSkipsBothAutomaticAndManualLookups() throws Exception {
        bedrock.set(true);
        assertFalse(bridge.findFallback(core, request, "player").get(5, TimeUnit.SECONDS).isPresent());
        assertFalse(bridge.findManual(core, request, "player").get(5, TimeUnit.SECONDS).isPresent());
        verifyNoInteractions(storage, core);
    }

    @Test
    void manualUpdateStillWorksWhenAutomaticSkinsAreDisabled() throws Exception {
        automatic.set(false);
        RemoteGameProfile skin = profile("value", "signature");
        when(core.getGameProfileByName("player")).thenReturn(CompletableFuture.completedFuture(Optional.of(skin)));
        assertSame(skin, bridge.findManual(core, request, "player").get(5, TimeUnit.SECONDS).get());
        verifyNoInteractions(storage);
    }

    @Test
    void lateHttpResultIsDiscardedAfterANewerSkinEvent() throws Exception {
        CompletableFuture<Optional<RemoteGameProfile>> http = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        when(core.getGameProfileByName("player")).thenAnswer(call -> { started.countDown(); return http; });
        CompletableFuture<Optional<RemoteGameProfile>> result = bridge.findManual(core, request, "player");
        assertTrue(started.await(5, TimeUnit.SECONDS));
        SkinApplyEvent external = event(SkinProperty.of("new", "signature"));
        bridge.onSkinApply(uuid, external);
        http.complete(Optional.of(profile("old", "signature")));
        assertFalse(result.get(5, TimeUnit.SECONDS).isPresent());
        verify(external, never()).setCancelled(anyBoolean());
    }

    @Test
    void policyChangeDuringHttpRequestDiscardsAutomaticResult() throws Exception {
        when(storage.getSkinForPlayer(uuid, "player", false)).thenReturn(Optional.empty());
        CompletableFuture<Optional<RemoteGameProfile>> http = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        when(core.getGameProfileByName("player")).thenAnswer(call -> { started.countDown(); return http; });
        CompletableFuture<Optional<RemoteGameProfile>> result = bridge.findFallback(core, request, "player");
        assertTrue(started.await(5, TimeUnit.SECONDS));
        automatic.set(false);
        http.complete(Optional.of(profile("old", "signature")));
        assertFalse(result.get(5, TimeUnit.SECONDS).isPresent());
    }

    @Test
    void staleQueuedApiEventIsCancelledAfterANewerSkinEvent() {
        SkinApplier<Object> applier = mock(SkinApplier.class);
        when(api.getSkinApplier(Object.class)).thenReturn(applier);
        assertTrue(bridge.applySkin(request, new Object(), Object.class, profile("old", "signature")));
        org.mockito.ArgumentCaptor<SkinProperty> submitted = org.mockito.ArgumentCaptor.forClass(SkinProperty.class);
        verify(applier).applySkin(any(), submitted.capture());
        bridge.onSkinApply(uuid, event(SkinProperty.of("new", "signature")));
        SkinApplyEvent queued = event(submitted.getValue());
        bridge.onSkinApply(uuid, queued);
        verify(queued).setCancelled(true);
    }

    @Test
    void currentOwnEventIsNotCancelledAndDoesNotInvalidateRequest() {
        SkinApplier<Object> applier = mock(SkinApplier.class);
        when(api.getSkinApplier(Object.class)).thenReturn(applier);
        doAnswer(call -> {
            SkinApplyEvent own = event(call.getArgument(1));
            bridge.onSkinApply(uuid, own);
            verify(own, never()).setCancelled(anyBoolean());
            return null;
        }).when(applier).applySkin(any(), any(SkinProperty.class));
        assertTrue(bridge.applySkin(request, new Object(), Object.class, profile("value", "signature")));
        assertTrue(bridge.isCurrent(request));
    }

    @Test
    void newRequestDisconnectCommandAndShutdownInvalidateOldWork() {
        bridge.begin(uuid);
        assertFalse(bridge.applySkin(request, new Object(), Object.class, profile("old", "signature")));
        request = bridge.begin(uuid);
        bridge.invalidate(uuid);
        assertFalse(bridge.isCurrent(request));
        request = bridge.begin(uuid);
        bridge.close();
        assertFalse(bridge.isCurrent(request));
        assertFalse(bridge.isCurrent(bridge.begin(uuid)));
        verify(api, never()).getSkinApplier(any());
    }


    @Test
    void identicalTexturesDoNotConfuseOldAndNewRequests() {
        SkinApplier<Object> applier = mock(SkinApplier.class);
        when(api.getSkinApplier(Object.class)).thenReturn(applier);
        Object player = new Object();
        assertTrue(bridge.applySkin(request, player, Object.class, profile("same", "signature")));
        request = bridge.begin(uuid);
        assertTrue(bridge.applySkin(request, player, Object.class, profile("same", "signature")));
        org.mockito.ArgumentCaptor<SkinProperty> values = org.mockito.ArgumentCaptor.forClass(SkinProperty.class);
        verify(applier, times(2)).applySkin(eq(player), values.capture());
        SkinApplyEvent oldEvent = event(values.getAllValues().get(0));
        bridge.onSkinApply(uuid, oldEvent);
        verify(oldEvent).setCancelled(true);
        SkinApplyEvent newEvent = event(values.getAllValues().get(1));
        bridge.onSkinApply(uuid, newEvent);
        verify(newEvent, never()).setCancelled(anyBoolean());
        assertTrue(bridge.isCurrent(request));
    }

    @Test
    void queuedEventIsCancelledAfterShutdown() {
        SkinApplier<Object> applier = mock(SkinApplier.class);
        when(api.getSkinApplier(Object.class)).thenReturn(applier);
        assertTrue(bridge.applySkin(request, new Object(), Object.class, profile("value", "signature")));
        org.mockito.ArgumentCaptor<SkinProperty> value = org.mockito.ArgumentCaptor.forClass(SkinProperty.class);
        verify(applier).applySkin(any(), value.capture());
        bridge.close();
        SkinApplyEvent queued = event(value.getValue());
        bridge.onSkinApply(uuid, queued);
        verify(queued).setCancelled(true);
    }

    @Test
    void eventBusSubscriptionUsesPlatformPlayerUuid() {
        net.skinsrestorer.api.event.EventBus bus = mock(net.skinsrestorer.api.event.EventBus.class);
        when(api.getEventBus()).thenReturn(bus);
        Object plugin = new Object();
        Object player = new Object();
        bridge.watch(plugin, Object.class, ignored -> uuid);
        org.mockito.ArgumentCaptor<java.util.function.Consumer<SkinApplyEvent>> callback =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(bus).subscribe(eq(plugin), eq(SkinApplyEvent.class), callback.capture());
        SkinApplyEvent external = event(SkinProperty.of("external", "signature"));
        when(external.getPlayer(Object.class)).thenReturn(player);
        callback.getValue().accept(external);
        assertFalse(bridge.isCurrent(request));
        verify(external, never()).setCancelled(anyBoolean());
    }


    @Test
    void automaticFallbackCannotStartAfterAnExternalEventEvenBeforeTexturesAppear() {
        bridge.disconnect(uuid);
        bridge.onSkinApply(uuid, event(SkinProperty.of("pending-refresh", "signature")));
        assertFalse(bridge.isCurrent(bridge.beginAutomatic(uuid)));
        // An explicit later manual request remains allowed.
        assertTrue(bridge.isCurrent(bridge.begin(uuid)));
    }

    @Test
    void automaticFallbackCannotReplaceManualWorkAndDisconnectResetsSession() {
        assertFalse(bridge.isCurrent(bridge.beginAutomatic(uuid)));
        assertTrue(bridge.isCurrent(request));
        bridge.disconnect(uuid);
        SkinsRestorerBridge.Request automaticRequest = bridge.beginAutomatic(uuid);
        assertTrue(bridge.isCurrent(automaticRequest));
        bridge.disconnect(uuid);
        assertFalse(bridge.isCurrent(automaticRequest));
    }

    private static SkinApplyEvent event(SkinProperty property) {
        SkinApplyEvent event = mock(SkinApplyEvent.class);
        when(event.getProperty()).thenReturn(property);
        return event;
    }

    private static RemoteGameProfile profile(String value, String signature) {
        return new RemoteGameProfile("id", "player", Collections.singletonList(
                new RemoteGameProfile.Property("textures", value, signature)));
    }
}
