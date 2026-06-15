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

package com.duom.ardamaps.core.commands;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.guide.GuideScreenLink;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.EmptyPacket;
import com.duom.ardamaps.gui.screens.ConfigurationScreen;
import com.duom.ardamaps.gui.screens.GuideScreen;
import com.duom.ardamaps.gui.screens.MapScreen;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Client-side commands for the Arda Maps mod.
 */
public class ClientCommands {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCommands.class);

    /** Tab spacing constant for formatted command output. */
    public static final String TAB_SPACING = "    ";
    public static final String DOUBLE_TAB_SPACING = TAB_SPACING + TAB_SPACING;

    /**
     * Registers client-side commands.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerCommands(dispatcher)
        );
    }

    /**
     * Registers the commands with the given dispatcher.
     *
     * @param dispatcher The command dispatcher.
     */
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(
                ClientCommandManager.literal(ArdaMaps.MOD_ID)
                        .executes(ClientCommands::printModInformation)
                        .then(ClientCommandManager.literal("guide")
                            .then(ClientCommandManager.argument("link", StringArgumentType.greedyString())
                                .executes(ClientCommands::openGuideLink)))
                        .then(ClientCommandManager.literal("waypoint")
                                .then(ClientCommandManager.argument("waypointCommandArgs", StringArgumentType.greedyString())
                                        .executes(ClientCommands::addChatWaypoint)))
                        .then(ClientCommandManager.literal("refresh")
                                .then(ClientCommandManager.literal("locations")
                                        .executes(ClientCommands::refreshLocations))
                                .then(ClientCommandManager.literal("regions")
                                        .executes(ClientCommands::refreshRegions))
                                .then(ClientCommandManager.literal("configuration")
                                        .executes(ClientCommands::refreshMaps)))
                        .then(ClientCommandManager.literal("debug")
                                .executes(ClientCommands::debugModState)
                                .then(ClientCommandManager.literal("exploration_state")
                                        .executes(ClientCommands::dumpExplorationState)
                                )
                        )
        );
    }

    /**
     * Command execution method to print the mod version information to the client.
     *
     * @param context The command context.
     * @return Command result status.
     */
    private static int printModInformation(CommandContext<FabricClientCommandSource> context) {

        String version = FabricLoader.getInstance()
                .getModContainer(ArdaMaps.MOD_ID)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString())
                .orElse("unknown");

        var contextSource = context.getSource();

        contextSource.sendFeedback(Text.literal("ArdaMaps - Version " + version));
        contextSource.sendFeedback(Text.literal("Commands: "));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID).formatted(Formatting.AQUA).append(Text.literal(" - Print mod information").formatted(Formatting.GRAY)));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID + " refresh locations").formatted(Formatting.AQUA).append(Text.literal(" - Re-synchronizes the location data from the server").formatted(Formatting.GRAY)));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID + " refresh regions").formatted(Formatting.AQUA).append(Text.literal(" - Re-synchronizes the regions LUT from the server").formatted(Formatting.GRAY)));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID + " refresh configuration").formatted(Formatting.AQUA).append(Text.literal(" - Re-synchronizes the maps configuration from the server").formatted(Formatting.GRAY)));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID + " debug").formatted(Formatting.AQUA).append(Text.literal(" - Print information on the current state of the mod for debugging purposes").formatted(Formatting.GRAY)));
        contextSource.sendFeedback(Text.literal("/" + ArdaMaps.MOD_ID + " debug exploration_state").formatted(Formatting.AQUA).append(Text.literal(" - Dump the current Exploration fog texture to disk for debugging purposes").formatted(Formatting.GRAY)));

        // Give the player a guidebook if they don't have one, or switch to it if they do
        PacketRegistry.GUIDEBOOK_REQUEST_HANDLER.send(new EmptyPacket());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes the location configuration
     *
     * @param context the command context
     * @return Command result status
     */
    @SuppressWarnings("unused")
    private static int refreshLocations(CommandContext<FabricClientCommandSource> context) {

        LOGGER.info("Refreshing location data");

        ArdaMapsClient.CONFIG.getLocationConfig().setLastUpdate(null);
        ArdaMapsClient.refreshLocations();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes the region LUT
     *
     * @param context the command context
     * @return Command result status.
     */
    @SuppressWarnings("unused")
    private static int refreshRegions(CommandContext<FabricClientCommandSource> context) {

        LOGGER.info("Refreshing Regions data");

        ArdaMapsClient.CONFIG.setRegionLookupTexture(RegionLookupTexture.DEFAULT);
        ArdaMapsClient.refreshRegionsLut();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes the map sources
     *
     * @param context the command context
     * @return Command result status.
     */
    @SuppressWarnings("unused")
    private static int refreshMaps(CommandContext<FabricClientCommandSource> context) {

        LOGGER.info("Refreshing Map layer data");

        ArdaMapsClient.refreshMapSources();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Command execution method to print the current mod state for debugging purposes.
     *
     * @param context The command context.
     * @return Command result status.
     */
    private static int debugModState(CommandContext<FabricClientCommandSource> context) {

        var contextSource = context.getSource();
        var dimensions = ArdaMapsClient.CONFIG.getDimensions();

        contextSource.sendFeedback(Text.literal("ArdaMaps - Mod State"));

        // Dimension data
        contextSource.sendFeedback(Text.literal(Integer.toString(dimensions.size())).formatted(Formatting.YELLOW).append(Text.literal(" Dimensions loaded\n{").formatted(Formatting.WHITE)));

        for (var dimension : dimensions) {

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Dimension ID: ")
                    .append(Text.literal(dimension.getId()).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Auto Generated: ")
                    .append(Text.literal(Boolean.toString(dimension.isAutoGenerated())).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- min_x {").append(Text.literal(Integer.toString(dimension.getXMin())).formatted(Formatting.AQUA)).append("}")
                    .append(", min_z {").append(Text.literal(Integer.toString(dimension.getZMin())).formatted(Formatting.AQUA)).append("}")
                    .append(", max_x {").append(Text.literal(Integer.toString(dimension.getXMax())).formatted(Formatting.AQUA)).append("}")
                    .append(", max_z {").append(Text.literal(Integer.toString(dimension.getZMax())).formatted(Formatting.AQUA)).append("}")
            );

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Width {").append(Text.literal(Integer.toString(dimension.getWidth())).formatted(Formatting.AQUA)).append("}")
                    .append(", Height {").append(Text.literal(Integer.toString(dimension.getHeight())).formatted(Formatting.AQUA)).append("}"));

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Scale ").append(Text.literal(Float.toString(dimension.getScale())).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Map Layers :"));

            for (var layer : dimension.getMapLayers()) {

                contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Name: ")
                        .append(Text.literal(layer.layer()).formatted(Formatting.AQUA)));

                contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Type: ")
                        .append(Text.literal(layer.type().toString()).formatted(Formatting.AQUA)));

                contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Remote: ")
                        .append(Text.literal(Boolean.toString(layer.remote())).formatted(Formatting.AQUA)));
            }
            contextSource.sendFeedback(Text.literal("\n"));
        }
        contextSource.sendFeedback(Text.literal("}"));

        var progress = ArdaMapsClient.CONFIG.getClientProgress();
        contextSource.sendFeedback(Text.literal("Exploration state for ").append(Text.literal(Integer.toString(progress.getExplorationState().size())).formatted(Formatting.YELLOW)).append(" dimensions : \n{"));

        for (var entry : progress.getExplorationState().entrySet()) {

            var exploration = entry.getValue();

            contextSource.sendFeedback(Text.literal(TAB_SPACING + "- Dimension ID: ")
                    .append(Text.literal(entry.getKey()).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Auto-generated: ")
                    .append(Text.literal(Boolean.toString(exploration.isAutoGenerated())).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Cell Size: ")
                    .append(Text.literal(Integer.toString(exploration.getCellSize())).formatted(Formatting.AQUA)));

            contextSource.sendFeedback(Text.literal(DOUBLE_TAB_SPACING + "- Mask size : {")
                    .append(Text.literal(Integer.toString(exploration.getNbCellsX())).formatted(Formatting.AQUA))
                    .append(Text.literal(","))
                    .append(Text.literal(Integer.toString(exploration.getNbCellsY())).formatted(Formatting.AQUA))
                    .append(Text.literal("}"))
            );
        }
        contextSource.sendFeedback(Text.literal("}"));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Opens the appropriate ArdaMaps screen for the given guide deep-link.
     *
     * <p>Resolves the {@code link} argument using the {@link GuideScreenLink} helpers:</p>
     * <ul>
     *   <li>{@code guide:map} - opens the Map screen</li>
     *   <li>{@code guide:configuration} - opens the Configuration screen</li>
     *   <li>Any other {@code guide:…} token - opens the Guide screen, which internally
     *       resolves the page/entry deep-link via {@link GuideScreenLink#resolve}</li>
     * </ul>
     *
     * <p>The screen is opened with {@code null} as parent so that closing it returns
     * the player straight to the game rather than back to a previous screen.</p>
     *
     * @param context the command context containing the {@code link} argument
     * @return {@link Command#SINGLE_SUCCESS}
     */
    private static int openGuideLink(CommandContext<FabricClientCommandSource> context) {

        String link = StringArgumentType.getString(context, "link");

        Client.mc().setScreen(null);

        // Schedule the screen to open on the next client tick, after the chat screen has closed.
        // Calling setScreen() directly here (or inside mc.execute()) races with the chat screen's
        // own setScreen(null) dismissal call, causing the new screen to be immediately hidden.
        if (GuideScreenLink.isMapLink(link))
            ArdaMapsClient.pendingScreen = new MapScreen(null);
        else if (GuideScreenLink.isConfigLink(link))
            ArdaMapsClient.pendingScreen = new ConfigurationScreen(null);
        else
            ArdaMapsClient.pendingScreen = new GuideScreen(null, link);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Adds a waypoint to the client via a chatcommand
     * @param context the command context containing the {@code waypoint} argument
     * @return {@link Command#SINGLE_SUCCESS}
     */
    private static int addChatWaypoint(CommandContext<FabricClientCommandSource> context) {

        String waypointCommandArgs = StringArgumentType.getString(context, "waypointCommandArgs");

        Optional<Waypoint> waypoint = Waypoint.fromJson(waypointCommandArgs);
        waypoint.ifPresent(value -> ArdaMapsClient.CONFIG.setWaypoint(value));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Command execution method to dump the current Fog of War texture to disk for debugging purposes.
     *
     * @param context The command context.
     * @return Command result status.
     */
    private static int dumpExplorationState(CommandContext<FabricClientCommandSource> context) {

        var progress = ArdaMapsClient.CONFIG.getClientProgress();

        for (var entry : progress.getExplorationState().entrySet()) {

            TextureManager textureManager = Client.mc().getTextureManager();
            Identifier textureId = entry.getValue().getFogTextureId();
            NativeImageBackedTexture texture = (NativeImageBackedTexture) textureManager.getTexture(textureId);

            try {
                Path dir = Client.mc().runDirectory.toPath().resolve("ardamaps");
                Files.createDirectories(dir);

                Path output = dir.resolve("fov_dump.png");
                if (texture.getImage() != null) texture.getImage().writeTo(output);
                else LOGGER.error("FoW texture image is null");

            } catch (IOException e) {
                LOGGER.error("Failed to dump FoW texture", e);
            }

            Client.mc().getTextureManager().destroyTexture(textureId);
        }

        context.getSource().sendFeedback(Text.literal("FoW texture dumped to disk"));

        return Command.SINGLE_SUCCESS;
    }
}