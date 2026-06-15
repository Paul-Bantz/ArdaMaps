/*
 * This file is part of ArdaMaps, licensed under the MIT License (MIT).
 *
 * Copyright (c) Paul-Bantz <https://github.com/Paul-Bantz>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.duom.ardamaps;

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.KeyBinds;
import com.duom.ardamaps.core.commands.ClientCommands;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.ClientConfigManager;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.config.client.ClientConfig;
import com.duom.ardamaps.core.data.guide.ArdaMapsChatLinkProcessor;
import com.duom.ardamaps.core.data.guide.GuideImageCache;
import com.duom.ardamaps.core.data.guide.GuideScreenLink;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.location.LocationProvider;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.core.data.map.providers.HttpImageProvider;
import com.duom.ardamaps.core.items.ModItems;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.EmptyPacket;
import com.duom.ardamaps.core.networking.packets.server.LocationsRequestPacket;
import com.duom.ardamaps.core.networking.packets.server.RegionsLutRequestPacket;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.hud.compass.Compass;
import com.duom.ardamaps.gui.hud.toposcope.Toposcope;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.map.rendering.BlueMapTileShader;
import com.duom.ardamaps.gui.map.rendering.FogOfWarShader;
import com.duom.ardamaps.gui.screens.ConfigurationScreen;
import com.duom.ardamaps.gui.screens.GuideScreen;
import com.duom.ardamaps.gui.screens.MapScreen;
import com.duom.ardamaps.gui.widgets.ToastWidget;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The client-side initializer for the Arda Maps mod, responsible for setting up client-specific features such as the toposcope and compass HUD elements, handling resource reloads, and managing player exploration tracking.
 * This class implements the ClientModInitializer interface from the Fabric API, allowing it to run initialization code when the client mod is loaded.
 */
public class ArdaMapsClient implements ClientModInitializer {

    /** Logger instance for the mod. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdaMapsClient.class);

    /** Executor for asynchronous processing */
    public static final ExecutorService IMAGE_EXECUTOR =
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
            );

    /** HTTP image provider instance */
    private static HttpImageProvider HTTP_IMAGE_PROVIDER;

    /** How long (ms) the near-locations cache is valid before refreshing. */
    public static final long LOCATION_CACHE_MS = 100L;

    /** Minimum squared distance to discover a location */
    public static final double LOCATION_NEAR_DISTANCE = 625d;

    /** Maximum number of toasts that may be queued at once. Excess toasts are silently dropped. */
    private static final int MAX_TOAST_QUEUE_SIZE = 5;

    /**
     * FIFO queue of pending {@link ToastWidget} toasts. Only the head is rendered at any time.
     * Entries are removed once their lifetime ({@link ToastWidget#TOTAL_MS}) has elapsed.
     */
    private static final Deque<ToastWidget> TOAST_QUEUE = new ArrayDeque<>();

    /** Client configuration manager instance, responsible for loading and saving client-specific configurations such as map layers and player progress. */
    public static ClientConfigManager CONFIG_MANAGER;

    /** Client configuration instance, containing settings and data relevant to the client's map experience, such as map layers, location data, and player exploration progress. */
    public static ClientConfig CONFIG;

    /**
     * Shared cache of nearby {@link LocationClient} objects for the current dimension,
     * covering the larger of the compass and toposcope draw distances.
     * The map is keyed by distance to the player
     * Updated every {@link #LOCATION_CACHE_MS} by {@link #refreshNearLocations}.
     */
    public static TreeMap<Double, LocationClient> NEAR_LOCATIONS = new TreeMap<>();

    /**
     * Screen scheduled to be opened on the next client tick (once the chat screen has closed).
     * Set by {@link com.duom.ardamaps.core.commands.ClientCommands} and consumed in {@link #clientTick}.
     */
    public static volatile Screen pendingScreen = null;

    /** Timestamp of the last {@link #NEAR_LOCATIONS} refresh. */
    private static long lastNearLocationsUpdate = 0L;

    /** Dimension ID used for the last {@link #NEAR_LOCATIONS} refresh - forces a hard refresh on dimension change. */
    private static String lastNearLocationsDimensionId = null;

    /** The toposcope HUD element, which provides directional information to the player. */
    private final Toposcope toposcope = new Toposcope();

    /** The compass HUD element, which provides orientation information to the player. */
    private final Compass compass = new Compass();

    /** Tracks the previous state of the right mouse button to detect clicks. */
    private boolean rightMouseButtonWasDown = false;

    /**
     * Initializes the client-side components of the Arda Maps mod, including setting up the configuration manager,
     * registering resource reload listeners, and setting up event handlers for player exploration tracking and data synchronization with the server.
     */
    @Override
    public void onInitializeClient() {

        HTTP_IMAGE_PROVIDER = new HttpImageProvider();
        CONFIG_MANAGER = new ClientConfigManager(
                "./config/arda-maps/config.json",
                "./config/arda-maps/locations.json",
                "./config/arda-maps/region-texture-lookup.json",
                "./config/arda-maps/progress.json"
        );
        CONFIG = CONFIG_MANAGER.getConfig();

        KeyBinds.register();

        this.registerModItems();
        this.registerResourceListeners();

        // Handle join world event to initialize mod internals and sync data with the server
        ClientPlayConnectionEvents.JOIN.register(this::initModInternals);

        // Register exploration tracking once here to avoid stacking listeners on every server join
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);

        // Handle stop event
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onStop);

        // Render queued toast notifications on the HUD
        HudRenderCallback.EVENT.register(this::renderToast);

        this.registerChatProcessor();

        ClientCommands.register();
    }

    /**
     * Registers the chat processing callbacks
     */
    private void registerChatProcessor() {

        // Style guide deep-links in incoming chat and system messages
        ClientReceiveMessageEvents.MODIFY_GAME.register(
                (message, overlay) -> ArdaMapsChatLinkProcessor.process(message));

        // Player-sent chat cannot be modified directly; intercept, cancel,
        // and re-add the styled version to ChatHud.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {

            Text processed = ArdaMapsChatLinkProcessor.process(message);

            if (processed == message) return true; // nothing to restyle

            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.inGameHud.getChatHud().addMessage(processed));

            return false; // cancel the unstyled original
        });
    }

    /**
     * Registers mod items such as the toposcope and compass.
     */
    private void registerModItems() {

        toposcope.registerRenderer();
        compass.registerRenderer();
    }

    /**
     * Registers resource reload listeners.
     */
    private void registerResourceListeners() {

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new MarkersLoaderReloadListener());

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new ShaderLoaderReloadListener());

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new IconSpriteAtlasReloadListener());

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new GuideImageCacheReloadListener());
    }

    /**
     * Initializes mod internals when the client joins a world, including loading player exploration data and refreshing map sources, location data, and region LUT from the server.
     *
     * @param clientPlayNetworkHandler The network handler for the client play connection.
     * @param packetSender             The packet sender for sending requests to the server.
     * @param client                   The Minecraft client instance.
     */
    @SuppressWarnings("unused")
    private void initModInternals(ClientPlayNetworkHandler clientPlayNetworkHandler, PacketSender packetSender, MinecraftClient client) {

        if (client.isInSingleplayer()) {
            LOGGER.info("ArdaMaps is a client/server mod - skipping initialization");
            return;
        }

        LOGGER.info("Joined world, initializing mod internals");

        // initializeTextures() calls registerDynamicTexture which requires the render/GL thread.
        client.execute(() -> ArdaMapsClient.CONFIG.getClientProgress().initializeTextures());

        // Network request dispatches
        ArdaMapsClient.refreshLocations();
        ArdaMapsClient.refreshMapSources();
        ArdaMapsClient.refreshRegionsLut();
    }

    /**
     * Handles client tick events to track player exploration and manage the toposcope overlay and interactions.
     *
     * @param client The Minecraft client instance.
     */
    private void clientTick(MinecraftClient client) {

        if (client.player == null) return;

        if (client.world == null) return;

        if (client.isInSingleplayer()) return;

        // Poll keybindings and update toggle state first.
        KeyBinds.tick();

        trackLocationDiscovery(client.player);
        trackExploration(client.player);

        // Refresh the shared near-locations cache once per tick budget.
        refreshNearLocations(client.player);

        // Open the map screen when M is pressed (independent of right-click).
        if (KeyBinds.consumeMapPress() && client.currentScreen == null) {

            Client.mc().setScreen(new MapScreen(null));
        }

        // Open a screen requested by a client command (deferred to avoid the chat-screen close race).
        if (pendingScreen != null && client.currentScreen == null) {
            Screen screen = pendingScreen;
            pendingScreen = null;
            Client.mc().setScreen(screen);
        }

        toposcope.toggleOverlay(client, KeyBinds.isToposcopeEnabled() || isHoldingGuidebook(client));
        toposcope.handleMouseClick(client);

        // Prevent guidebook interaction when hovering over a location to avoid conflicts between the toposcope overlay and the guidebook UI.
        if (!toposcope.isHoveringLocation())
            handleGuidebookDisplay(client);
    }

    /**
     * Handles client stopping event. Clean up executors to prevent resource leaks.
     *
     * @param client The Minecraft client instance.
     */
    @SuppressWarnings("unused")
    private void onStop(MinecraftClient client) {

        ArdaMapsClient.CONFIG_MANAGER.save();

        // Shutdown the image executor
        IMAGE_EXECUTOR.shutdown();
        try {
            if (!IMAGE_EXECUTOR.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                IMAGE_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IMAGE_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Flush and close the disk cache and scheduler
        ArdaMapsClient.getHttpImageProvider().close();
    }

    /**
     * Renders a toast notification if there is one in the queue.
     *
     * @param drawContext the draw context
     * @param tickDelta   unused tick delta
     */
    private void renderToast(DrawContext drawContext, @SuppressWarnings("unused") float tickDelta) {

        ToastWidget head = TOAST_QUEUE.peek();

        if (head == null) return;

        if (!head.isAlive()) {

            TOAST_QUEUE.poll();
            head = TOAST_QUEUE.peek();

            if (head == null) return;
        }

        head.render(drawContext);
    }

    /**
     * Refreshes the location data from the server.
     */
    public static void refreshLocations() {

        ArdaMaps.IO_EXECUTOR.submit(() -> {

            Date lastUpdate = ArdaMapsClient.CONFIG.getLocationConfig().getLastUpdate();
            PacketRegistry.LOCATIONS_UPDATE_REQUEST.send(new LocationsRequestPacket(lastUpdate), response -> {

                LocationConfig<LocationClient> data = response.data();

                if (data != null) {

                    CONFIG_MANAGER.synchronizeAndUpdateLocationExplorationProgress(data);
                    LOGGER.info("Location data updated from server.");

                } else {

                    LOGGER.info("Location data is up to date.");
                }

                // Reinit map markers here since location data might have changed
                MarkersManager.rebind();
            });
        });
    }

    /**
     * Refresh the map sources from the server when joining a world.
     */
    public static void refreshMapSources() {

        PacketRegistry.MAP_SOURCES_REQUEST.send(new EmptyPacket(), response -> {

            List<Dimension> dimensions = response.dimensions();

            if (dimensions.isEmpty()) {
                LOGGER.warn("No dimension data received from server. Check server configuration and ensure that the map source request handler is properly set up.");
                return;
            }

            // Initialize per-dimension fog-of-war textures
            MinecraftClient.getInstance().execute(() -> {

                LOGGER.info("Received Dimension Data from server: {} dimension(s) found.", dimensions.size());
                ArdaMapsClient.CONFIG.setDimensions(dimensions);

                // Invalidate cached dimension to ensure it is re-resolved with the updated dimension data on next access.
                Client.invalidateCachedDimension();

                ArdaMapsClient.CONFIG.getClientProgress().reset(true);
                LOGGER.info("Per-dimension exploration instances initialised ({} dimension(s)).",
                        dimensions.size());
            });
        });
    }

    /**
     * Refreshes the regions LUT from the server.
     */
    public static void refreshRegionsLut() {

        ArdaMaps.IO_EXECUTOR.submit(() -> {
            Date lastUpdate = ArdaMapsClient.CONFIG.getRegionLookupTexture().lastUpdate();
            PacketRegistry.REGION_LUT_UPDATE_REQUEST.send(new RegionsLutRequestPacket(lastUpdate), regionsLutResponsePacket -> {

                RegionLookupTexture data = regionsLutResponsePacket.data();

                if (data != null) {

                    ArdaMapsClient.CONFIG.setRegionLookupTexture(data);
                    ArdaMapsClient.CONFIG_MANAGER.saveRegionTextureLookup();
                    LOGGER.info("Region LUT data updated from server.");

                } else {

                    LOGGER.info("Region LUT data is up to date.");
                }
            });
        });
    }

    /**
     * Tracks location discovery by checking if the player is within a certain distance of any undiscovered locations
     * and marking them as visited in the player's progress.
     *
     * @param player the current player instance
     */
    private void trackLocationDiscovery(@NotNull ClientPlayerEntity player) {

        if (ArdaMapsClient.CONFIG.isMapRevealAll()) return;
        if (NEAR_LOCATIONS.isEmpty()) return;

        var nearestSquaredDistance = NEAR_LOCATIONS.firstKey();

        if (nearestSquaredDistance <= LOCATION_NEAR_DISTANCE) {

            var nearestLocation = NEAR_LOCATIONS.firstEntry().getValue();

            if (!nearestLocation.isVisited()) {

                nearestLocation.setVisited(true);

                // Don't spam toasts on client start
                if (player.age > 20)
                    showToast(new ToastWidget(
                            Text.translatable("ardamaps.client.notification.location.visited", nearestLocation.getName()),
                            ModConstants.ICON_BOOK
                    ));

                ArdaMapsClient.CONFIG_MANAGER.saveProgress();
            }
        }
    }

    /**
     * Tracks player exploration each tick.
     *
     * <p>Resolves the {@link PlayerExploration} instance for the player's current dimension
     * and marks the player's cell as {@link ExplorationState#REVEALED}.  Also keeps the
     * static active instance in sync so that UI code using the backward-compat delegates
     * always operates on the correct dimension.</p>
     */
    private void trackExploration(@NotNull ClientPlayerEntity player) {

        var progress = CONFIG.getClientProgress();

        /*
         * When client starts, player position might be at (0, 0) before the first world update packet is received from the server.
         * That can lead to an exploration tick at 0,0 - this tick should be discarded
         */
        if (player.age < 100) return;

        // Get the dimension ID the player is currently in.
        String dimensionId = Client.currentDimensionId();

        PlayerExploration exploration = progress.getExplorationState(dimensionId, true);

        /*
         If exploration is null, the client might have not received the dimension data from the server yet,
         Silently ignore exploration tracking until the dimension data is available.
         This can happen when the player first joins a world and the client is still syncing data with the server. or
         the server is misconfigured.
         */
        if (exploration == null) return;

        int cellX = exploration.toCellX(player.getX());
        int cellZ = exploration.toCellZ(player.getZ());

        if (exploration.stateAt(cellX, cellZ) != ExplorationState.REVEALED) {//Tick -1455.6197844287037 -675.1320489133536

            exploration.markCell(cellX, cellZ, ExplorationState.REVEALED, 2);
            exploration.flushTexture();

            // After marking cells revealed, mark any locations within that area as explored
            ArdaMapsClient.CONFIG.getLocations(dimensionId, null).stream()
                    .filter(location -> location.getExplorationState().ordinal() < ExplorationState.REVEALED.ordinal())
                    .filter(location -> {
                        Vec3d pos = location.getPosition();
                        return pos != null && exploration.isWorldPosExplored(pos.x, pos.z, 0);
                    })
                    .forEach(location -> location.updateExplorationState(ExplorationState.REVEALED));

            ArdaMapsClient.CONFIG_MANAGER.saveProgress();
        }
    }

    /**
     * Refreshes {@link #NEAR_LOCATIONS} if the cache has expired or the player has changed dimension.
     *
     * <p>The cache range is the larger of the compass and toposcope draw distances so that both
     * renderers can always filter down from the shared list without needing their own
     * {@link LocationProvider} calls. The result is sorted ascending by squared distance to the
     * player, which is cheap (no {@code sqrt}) and gives the toposcope a pre-sorted base.</p>
     *
     * @param player The current client player.
     */
    private static void refreshNearLocations(@org.jetbrains.annotations.NotNull ClientPlayerEntity player) {

        String currentDimensionId = Client.currentDimensionId();
        long now = System.currentTimeMillis();

        boolean dimensionChanged = !Objects.equals(currentDimensionId, lastNearLocationsDimensionId);
        boolean cacheExpired = (now - lastNearLocationsUpdate) > LOCATION_CACHE_MS;

        if (!dimensionChanged && !cacheExpired) return;

        var dimension = Client.currentDimension();
        float compassRange = CONFIG.getCompassDrawDistanceBlocks(dimension);
        float toposcopeRange = CONFIG.getToposcopeDrawDistanceBlocks(dimension);
        float maxRange = Math.max(compassRange, toposcopeRange);

        var playerPos = player.getPos();

        NEAR_LOCATIONS = LocationProvider.getLocations(currentDimensionId, playerPos, maxRange, true);
        lastNearLocationsUpdate = now;
        lastNearLocationsDimensionId = currentDimensionId;
    }

    /**
     * Checks if the player is currently holding the guidebook in either hand, which should also trigger the toposcope overlay to be displayed.
     *
     * @param client the client instance
     * @return true if the client is holding the guidebook false otherwise
     */
    private boolean isHoldingGuidebook(MinecraftClient client) {

        if (client == null || client.player == null) return false;

        return client.player.getMainHandStack().isOf(ModItems.GUIDEBOOK)
                || client.player.getOffHandStack().isOf(ModItems.GUIDEBOOK);
    }

    /**
     * Handles mouse click events to detect right-clicks and open the last-remembered ArdaMaps screen.
     *
     * <p>Resolves {@code ClientConfig#lastPage} to determine which screen to open:</p>
     * <ul>
     *   <li>{@code "guide:map"} - opens the Map screen</li>
     *   <li>{@code "guide:configuration"} - opens the Configuration screen</li>
     *   <li>{@code "guide"} or any guide page link - opens the Guide screen, restoring the last sub-page</li>
     *   <li>Unresolvable links - falls back to the Guide landing page</li>
     * </ul>
     *
     * @param client The Minecraft client instance.
     */
    public void handleGuidebookDisplay(MinecraftClient client) {

        long handle = client.getWindow().getHandle();
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (rightDown && !rightMouseButtonWasDown && client.currentScreen == null && isHoldingGuidebook(client)) {

            String lastPage = CONFIG.getLastPage();

            if (GuideScreenLink.isMapLink(lastPage)) {
                Client.mc().setScreen(new MapScreen(null));
            } else if (GuideScreenLink.isConfigLink(lastPage)) {
                Client.mc().setScreen(new ConfigurationScreen(null));
            } else {
                // "guide" (landing) or "guide:page:<pageId>/<entryId>" - GuideScreen resolves internally
                Client.mc().setScreen(new GuideScreen(null, lastPage));
            }
        }

        rightMouseButtonWasDown = rightDown;
    }

    /**
     * Enqueues a {@link ToastWidget} for display. If the queue is already at {@link #MAX_TOAST_QUEUE_SIZE}
     * the new toast is silently dropped to prevent flooding the screen.
     *
     * @param toast the toast to enqueue
     */
    public static void showToast(ToastWidget toast) {
        if (TOAST_QUEUE.size() < MAX_TOAST_QUEUE_SIZE) {
            TOAST_QUEUE.addLast(toast);
        }
    }

    /**
     * A resource reload listener that loads markers definitions when client resources are reloaded.
     */
    private static class MarkersLoaderReloadListener implements SimpleSynchronousResourceReloadListener {

        /** The identifier for this resource reload listener, used to distinguish it from other listeners. */
        @Override
        public Identifier getFabricId() {
            return new Identifier(ArdaMaps.MOD_ID, "markers_loader");
        }

        /** Loads all custom shaders when client resources are reloaded. */
        @Override
        public void reload(ResourceManager manager) {

            MarkersManager.reload();
        }
    }

    /**
     * A resource reload listener that loads the fog of war shader when client resources are reloaded.
     */
    private static class ShaderLoaderReloadListener implements SimpleSynchronousResourceReloadListener {

        /** The identifier for this resource reload listener, used to distinguish it from other listeners. */
        @Override
        public Identifier getFabricId() {
            return new Identifier(ArdaMaps.MOD_ID, "shader_loader");
        }

        /** Loads all custom shaders when client resources are reloaded. */
        @Override
        public void reload(ResourceManager manager) {

            FogOfWarShader.load(manager);
            BlueMapTileShader.load(manager);
        }
    }

    /**
     * A resource reload listener that manages the icon sprite atlas, ensuring it is reloaded when client resources are reloaded.
     */
    private static class IconSpriteAtlasReloadListener implements IdentifiableResourceReloadListener {

        /** The icon sprite atlas instance, which is responsible for managing the icons used in the mod's HUD and map rendering. */
        private IconSpriteAtlas atlas;

        /** Returns the identifier for this resource reload listener, which is used to distinguish it from other listeners. */
        @Override
        public Identifier getFabricId() {
            return new Identifier(ArdaMaps.MOD_ID, "icon_sprite_atlas");
        }

        /** Reloads the icon sprite atlas when client resources are reloaded, ensuring that any changes to the icons are reflected in the mod's HUD and map rendering. */
        @Override
        public CompletableFuture<Void> reload(
                Synchronizer synchronizer, ResourceManager manager,
                Profiler prepareProfiler, Profiler applyProfiler,
                Executor prepareExecutor, Executor applyExecutor) {

            if (atlas == null) {
                atlas = IconSpriteAtlas.create(MinecraftClient.getInstance().getTextureManager());
            }
            return atlas.reload(synchronizer, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
        }
    }

    /**
     * A resource reload listener that clears the {@link GuideImageCache} when client
     * resources are reloaded, so guide images are re-read from the updated resource pack.
     */
    private static class GuideImageCacheReloadListener implements SimpleSynchronousResourceReloadListener {

        @Override
        public Identifier getFabricId() {
            return new Identifier(ArdaMaps.MOD_ID, "guide_image_cache");
        }

        @Override
        public void reload(ResourceManager manager) {
            GuideImageCache.clear();
        }
    }

    /**
     * @return The HTTP image provider instance
     */
    public static HttpImageProvider getHttpImageProvider() {
        return HTTP_IMAGE_PROVIDER;
    }
}