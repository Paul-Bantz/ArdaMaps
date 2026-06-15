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

package com.duom.ardamaps.gui.widgets;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.consumers.ArdaPathsHook;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.conversion.ContentBlock;
import com.duom.ardamaps.core.data.conversion.HtmlConverter;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.location.LocationDetails;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.server.LocationDetailsRequestPacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerWarpPacket;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.MapScreen;
import com.duom.ardamaps.gui.screens.ScreenRenderingUtils;
import com.duom.ardamaps.gui.screens.rendering.TextContentBlockRenderer;
import com.duom.ardamaps.gui.widgets.builders.StyledButtonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A side panel widget that displays detailed information about a specific location on the map.
 * It includes a fade-in effect, scrollable description, and action buttons for setting waypoints
 * and teleporting to the location.
 */
public class SidePanelWidget implements Element {

    /** Layout constants */
    private static final int ELEMENT_SPACING = 2;

    /** Padding from the edges of the panel to the content */
    private static final int PADDING = 24;

    /** Width of the scrollbar area (including margin) */
    private static final int SCROLLBAR_WIDTH = 4;

    /** Margin between the scrollbar and the text content */
    private static final int SCROLLBAR_MARGIN = 3;

    /** Scroll speed */
    private static final int SCROLL_SPEED = 12;

    /** Scrollbar */
    private final ScrollbarWidget scrollbar = new ScrollbarWidget(4, 12, ModConstants.COLOR_BLUE, ModConstants.COLOR_BLUE_HIGHLIGHT, SCROLL_SPEED);

    /** Parent screen */
    private final Screen parent;

    /** The location to display details for */
    private final LocationClient displayedLocation;

    /** Text renderer for drawing text */
    private final TextRenderer textRenderer;

    /** The detailed information about the location */
    private LocationDetails locationDetails;

    /** Screen width */
    @Getter
    private int width;

    /** Screen height */
    private int height;

    /** Screen top left X */
    private int screenX1;

    /** Screen top left Y */
    private int screenY1;

    /** Screen bottom right X */
    private int screenX2;

    /** Screen bottom right Y */
    private int screenY2;

    /** Title x position to handle click event for focus */
    private int titleX;

    /** Title y position to handle click event for focus */
    private int titleY;

    /** Title width to handle click event for focus */
    private int titleWidth;

    /** Title height to handle click event for focus */
    private int titleHeight;

    /** Tracks the previous state of the left mouse button to detect clicks. */
    private boolean leftMouseButtonWasDown = false;

    /** Description blocks */
    private List<ContentBlock> descriptionBlocks = List.of();

    /** Set waypoint button */
    private StyledButtonWidget setWaypointButton;

    /** Teleport button */
    private StyledButtonWidget teleportButton;

    /** Explore in depth button */
    private StyledButtonWidget exploreInDepthButton;

    /** Renderer for the HTML content blocks */
    private final TextContentBlockRenderer textContentBlockRenderer;

    /** The camera offset when this location is focused */
    private final Vec2d cameraFocusWorldPosition;

    /** The camera zoom when this location is focused */
    private final double cameraFocusZoom;

    /**
     * Constructs a SidePanelWidget.
     *
     * @param parent                   The parent screen
     * @param textRenderer             The text renderer for drawing text
     * @param displayedLocation        The location to display details for
     * @param cameraFocusWorldPosition The camera focus offset in world coordinates
     * @param cameraFocusZoom          the camera focus zoom
     */
    public SidePanelWidget(Screen parent, TextRenderer textRenderer, LocationClient displayedLocation, Vec2d cameraFocusWorldPosition, double cameraFocusZoom) {

        this.parent = parent;
        this.textRenderer = textRenderer;
        this.displayedLocation = displayedLocation;
        this.textContentBlockRenderer = new TextContentBlockRenderer(textRenderer, ModConstants.COLOR_DARK_BROWN);
        this.cameraFocusWorldPosition = cameraFocusWorldPosition;
        this.cameraFocusZoom = cameraFocusZoom;

        init();
    }

    /**
     * Initializes the side panel by requesting location details from the server.
     */
    private void init() {

        fetchLocationDetails();

        /* Buttons */

        setWaypointButton = StyledButtonBuilder.create()
                .setText(Text.translatable("ardamaps.client.generic.set.waypoint"))
                .setOnClick(() -> ArdaMapsClient.CONFIG.setWaypoint(displayedLocation.getPosition().x, displayedLocation.getPosition().z, displayedLocation.getWorld()))
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .build();

        teleportButton = StyledButtonBuilder.create()
                .setText(Text.translatable("ardamaps.client.generic.teleport"))
                .setOnClick(this::requestTeleport)
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .build();

        exploreInDepthButton = StyledButtonBuilder.create()
                .setText(Text.translatable("ardamaps.client.map.screen.side.panel.explore_in_depth"))
                .setOnClick(this::exploreInDepth)
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .build();

        assert exploreInDepthButton != null;

        if (displayedLocation.isVisited()) {
            exploreInDepthButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.side.panel.explore_in_depth.tooltip")));
        } else {
            exploreInDepthButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.side.panel.explore_in_depth.not_visited.tooltip")));
            exploreInDepthButton.active = false;
        }
    }

    /**
     * Define the size of this panel
     *
     * @param width  the width of the panel
     * @param height the height of the panel
     */
    public void setSize(int width, int height) {

        this.width = width;
        this.height = height;
    }

    /**
     * Sets the position of this side panel on screen
     * @param x the top left x coordinate
     * @param y the top left y coordinate
     */
    public void setPosition(int x, int y) {

        screenX1 = x;
        screenY1 = y;
        screenX2 = x + width;
        screenY2 = y + height;
    }

    /**
     * Fetches location details from the server.
     */
    private void fetchLocationDetails() {

        if (!displayedLocation.isRevealed()) {

            var placeholderText = Text.translatable("ardamaps.client.map.screen.side.panel.unexplored.description").getString();

            locationDetails = new LocationDetails(Text.translatable("ardamaps.client.map.screen.side.panel.unexplored.location").getString());
            descriptionBlocks = HtmlConverter.parseBlocks(placeholderText);

        } else if (!displayedLocation.isVisited()) {

            var placeholderText = Text.translatable("ardamaps.client.map.screen.side.panel.unvisited.description").getString();

            locationDetails = new LocationDetails(Text.translatable("ardamaps.client.map.screen.side.panel.unvisited.location").getString());
            descriptionBlocks = HtmlConverter.parseBlocks(placeholderText);

        } else {

            LocationDetailsRequestPacket packet = new LocationDetailsRequestPacket(displayedLocation.getName());
            PacketRegistry.LOCATION_DETAILS_REQUEST.send(packet, locationDetailsResponsePacket -> {

                var details = locationDetailsResponsePacket.details();

                if (!details.description().isBlank()) {

                    locationDetails = locationDetailsResponsePacket.details();
                    descriptionBlocks = HtmlConverter.parseBlocks(locationDetails.description());
                }
            });
        }
    }

    /**
     * Explore in depth button click handler. Teleports the player to the given location and puts the pathfinder in
     * their hands.
     */
    private void exploreInDepth() {

        // Close the UI
        Client.mc().setScreen(null);

        String pathfinderNode = displayedLocation.getPathfinder();

        if (pathfinderNode == null || pathfinderNode.isEmpty()) return;

        String[] pathfinderData = pathfinderNode.split(":");

        if (pathfinderData.length == 2) {

            // Select path and chapter in the pathfinder
            ArdaPathsHook.selectPathfinderPathAndChapter(pathfinderData[0], pathfinderData[1], false);
            requestTeleport();
        }
    }

    /**
     * Sends a teleport request to the server for the displayed location
     */
    private void requestTeleport() {

        var warp = displayedLocation.getWarp();

        if (warp != null && !warp.isEmpty()) {

            PacketRegistry.PLAYER_WARP_REQUEST.send(new PlayerWarpPacket(warp));

        } else {

            PacketRegistry.PLAYER_TELEPORT_REQUEST.send(new PlayerTeleportPacket(
                    displayedLocation.getPosition().x,
                    displayedLocation.getPosition().z,
                    displayedLocation.getWorld()));
        }
    }

    /**
     * Renders the side panel with fade-in effect.
     *
     * @param context The drawing context
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     */
    public void render(DrawContext context, int mouseX, int mouseY) {

        if (locationDetails == null) return;

        renderBackground(context);
        renderGuiElements(context, mouseX, mouseY);
    }

    /**
     * Renders the side panel background with fade effect.
     *
     * @param context The drawing context
     */
    private void renderBackground(DrawContext context) {

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawNineSlicedTexture(ModConstants.PAPER_TEXTURE,
                screenX1, screenY1,
                screenX2 - screenX1, screenY2 - screenY1,
                64,
                64,
                64,
                64,
                256,
                256,
                0, 0);

        RenderSystem.disableBlend();
    }

    /**
     * Renders the GUI elements within the side panel.
     *
     * @param context The drawing context
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     */
    private void renderGuiElements(DrawContext context, int mouseX, int mouseY) {

        var centerX = (screenX1 + screenX2) / 2;
        var y = screenY1 + ELEMENT_SPACING + PADDING;
        var usableWidth = screenX2 - screenX1 - 2 * PADDING;
        var halfUsableWidth = usableWidth / 2;
        boolean hasProjectInfo = displayedLocation.getPathfinder() != null && !displayedLocation.getPathfinder().isEmpty();

        y += renderTitle(context, centerX, y, mouseX, mouseY) + ELEMENT_SPACING * 3;
        y += ScreenRenderingUtils.renderSeparator(context, usableWidth, screenX1 + PADDING, y) + ELEMENT_SPACING * 3;

        var bottomButtonsSpacing = ModConstants.BUTTON_HEIGHT + ELEMENT_SPACING + PADDING;
        if (hasProjectInfo) bottomButtonsSpacing += ModConstants.BUTTON_HEIGHT;

        var remainingVisibleHeight = screenY2 - y - bottomButtonsSpacing;

        y += renderDescription(context, mouseX, mouseY, usableWidth, centerX, y, halfUsableWidth, remainingVisibleHeight) + ELEMENT_SPACING;

        if (hasProjectInfo)
            y += renderExploreInDepth(context, centerX, y, mouseX, mouseY) + ELEMENT_SPACING * 3;

        renderButtons(context, usableWidth, screenX1, y, mouseX, mouseY);
    }

    /**
     * Renders the title of the location at the top of the side panel.
     *
     * @param context The drawing context
     * @param centerX The centre X coordinate of the panel
     * @param y       The Y coordinate for rendering the title
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     * @return The height of the rendered title
     */
    private int renderTitle(DrawContext context, int centerX, int y, int mouseX, int mouseY) {

        this.titleWidth = (int) (textRenderer.getWidth(locationDetails.name()) * ModConstants.H1_TEXT_SCALE);
        this.titleHeight = (int) (textRenderer.fontHeight * ModConstants.H1_TEXT_SCALE);
        this.titleX = centerX - (titleWidth / 2);
        this.titleY = y;

        context.getMatrices().push();
        context.getMatrices().translate(titleX, titleY, 0);
        context.getMatrices().scale(ModConstants.H1_TEXT_SCALE, ModConstants.H1_TEXT_SCALE, 1.0f);

        int color = mouseOverTitle(mouseX, mouseY) ?
                ModConstants.COLOR_BLUE_HIGHLIGHT :
                ModConstants.COLOR_BLUE;

        context.drawText(
                textRenderer,
                locationDetails.name(),
                0,
                0,
                color,
                false
        );

        context.getMatrices().pop();

        return (int) (textRenderer.fontHeight * ModConstants.H1_TEXT_SCALE);
    }

    /**
     * Renders the explore in-depth button of the location if more information is available.
     *
     * @param context The drawing context
     * @param centerX The centre X coordinate of the panel
     * @param y       The Y coordinate for rendering the button
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     * @return The height of the rendered button
     */
    private int renderExploreInDepth(DrawContext context, int centerX, int y, int mouseX, int mouseY) {

        exploreInDepthButton.setPosition(centerX - ModConstants.BUTTON_WIDTH / 2, y);
        exploreInDepthButton.render(context, mouseX, mouseY, 0);

        return ModConstants.BUTTON_HEIGHT;
    }

    /**
     * Checks if the mouse is over the title - used only if title is clickable (ie cameraFocusWorldPosition != null)
     *
     * @param mouseX the mouse x position
     * @param mouseY the mouse y position
     * @return true if the mouse is currently over the title
     */
    private boolean mouseOverTitle(int mouseX, int mouseY) {

        return cameraFocusWorldPosition != null &&
                mouseX >= titleX && mouseX <= titleX + titleWidth &&
                mouseY >= titleY && mouseY <= titleY + titleHeight;
    }

    /**
     * Renders the description content blocks within the side panel. Handles hover tooltips
     * and link-click events (pan-and-select on internal location links).
     *
     * @param context         The drawing context
     * @param mouseX          Current mouse X
     * @param mouseY          Current mouse Y
     * @param usableWidth     The usable width for text rendering
     * @param centerX         The centre X coordinate of the panel
     * @param y               The starting Y coordinate for rendering
     * @param halfUsableWidth Half of the usable width
     * @param visibleHeight   The visible height for rendering
     * @return The total height occupied by the description area (= visibleHeight)
     */
    private int renderDescription(DrawContext context, double mouseX, double mouseY,
                                  int usableWidth, int centerX, int y,
                                  int halfUsableWidth, int visibleHeight) {

        // Detect rising-edge left-click this frame
        long handle = Client.mc().getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = leftDown && !leftMouseButtonWasDown;
        leftMouseButtonWasDown = leftDown;

        int startX = centerX - halfUsableWidth;
        int textWrapWidth = usableWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        int viewportBottom = y + visibleHeight;

        TextContentBlockRenderer.RenderResult result = textContentBlockRenderer.render(
                context, descriptionBlocks,
                startX, y, textWrapWidth,
                viewportBottom,
                scrollbar.getScrollOffset(),
                (int) mouseX, (int) mouseY
        );

        int totalHeight = result.totalHeight;
        int maxScroll = Math.max(0, totalHeight - visibleHeight);
        scrollbar.setMaxOffset(maxScroll);
        if (maxScroll > 0) {
            scrollbar.render(context, startX + usableWidth - SCROLLBAR_WIDTH, y,
                    visibleHeight, visibleHeight, totalHeight);
        }

        // Handle hover tooltip and click events
        Style hoveredStyle = result.hoveredStyle;
        if (hoveredStyle != null) {
            if (hoveredStyle.getHoverEvent() != null)
                context.drawHoverEvent(textRenderer, hoveredStyle, (int) mouseX, (int) mouseY);

            if (clicked && hoveredStyle.getClickEvent() != null
                    && hoveredStyle.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                handleLinkClick(hoveredStyle.getClickEvent());
            }
        }

        return visibleHeight;
    }

    /**
     * Renders the action buttons within the side panel.
     *
     * @param context     The drawing context
     * @param usableWidth The usable width for button rendering
     * @param x           The X coordinate for rendering the buttons
     * @param y           The Y coordinate for rendering the buttons
     * @param mouseX      The current mouse X position
     * @param mouseY      The current mouse Y position
     */
    private void renderButtons(DrawContext context, int usableWidth, int x, int y, int mouseX, int mouseY) {

        var buttonWidth = (usableWidth - ELEMENT_SPACING) / 2;

        if (!displayedLocation.isRevealed() || !displayedLocation.isVisited()) {

            setWaypointButton.setX(x + PADDING + (usableWidth - ModConstants.BUTTON_WIDTH) / 2);
            setWaypointButton.setY(y);
            setWaypointButton.setWidth(ModConstants.BUTTON_WIDTH);
            setWaypointButton.render(context, mouseX, mouseY, 0f);

        } else {

            setWaypointButton.setX(x + PADDING);
            setWaypointButton.setY(y);
            setWaypointButton.setWidth(buttonWidth);
            setWaypointButton.render(context, mouseX, mouseY, 0f);

            teleportButton.setX(x + PADDING + buttonWidth + ELEMENT_SPACING);
            teleportButton.setY(y);
            teleportButton.setWidth(buttonWidth);
            teleportButton.render(context, mouseX, mouseY, 0f);
        }
    }

    /**
     * Pans the camera to the new selected location and select it
     *
     * @param clickEvent the initiating event
     */
    private void handleLinkClick(ClickEvent clickEvent) {

        if (parent != null && parent instanceof MapScreen) {

            ((MapScreen) parent).panAndSelectLocation(ArdaMapsClient.CONFIG.getLocation(clickEvent.getValue()), false);
        }
    }

    /**
     * Handles mouse movement events for the side panel. Unimplemented as hover effects are not required.
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {/* Unimplemented */}

    /**
     * Handles mouse click events for the side panel, checking if the click was on any of the buttons.
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     * @param button The mouse button that was clicked
     * @return True if the event was handled by the side panel, false otherwise
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (mouseOverTitle((int) mouseX, (int) mouseY)) {

            ((MapScreen) parent).panCameraToMapCoordinates(cameraFocusWorldPosition, cameraFocusZoom);
            return true;
        }

        return (exploreInDepthButton.mouseClicked(mouseX, mouseY, button))
                || (teleportButton.mouseClicked(mouseX, mouseY, button))
                || (setWaypointButton.mouseClicked(mouseX, mouseY, button));
    }

    /**
     * Handles mouse release events for the side panel. Unimplemented as button release effects are not required.
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     * @param button The mouse button that was released
     * @return False as the event is not handled
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handles mouse drag events for the side panel. Unimplemented as dragging is not required.
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     * @param button The mouse button that is being dragged
     * @param deltaX The change in X position since the last event
     * @param deltaY The change in Y position since the last event
     * @return False as the event is not handled
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Handle mouse scroll for zooming
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param amount The scroll amount
     * @return True if the event was handled
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {

        if (!isMouseOver(mouseX, mouseY)) return false;

        return scrollbar.scroll(amount);
    }

    /**
     * Checks if the mouse is currently over the side panel.
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     * @return True if the mouse is over the panel, false otherwise
     */
    public boolean isMouseOver(double mouseX, double mouseY) {

        return mouseX >= screenX1 && mouseX <= screenX2 &&
                mouseY >= screenY1 && mouseY <= screenY2;
    }

    /**
     * Handles key press events for the side panel. Unimplemented as keyboard interaction is not required.
     *
     * @param keyCode   The code of the key that was pressed
     * @param scanCode  The scan code of the key that was pressed
     * @param modifiers Any modifier keys that were held during the key press
     * @return False as the event is not handled
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handles key release events for the side panel. Unimplemented as keyboard interaction is not required.
     *
     * @param keyCode   The code of the key that was released
     * @param scanCode  The scan code of the key that was released
     * @param modifiers Any modifier keys that were held during the key release
     * @return False as the event is not handled
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handles character typing events for the side panel. Unimplemented as keyboard interaction is not required.
     *
     * @param chr       The character that was typed
     * @param modifiers Any modifier keys that were held during typing
     * @return False as the event is not handled
     */
    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    /**
     * Gets the navigation path for the side panel. Unimplemented as keyboard navigation is not required.
     *
     * @param navigation The GuiNavigation instance requesting the path
     * @return Null as navigation paths are not implemented
     */
    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }

    /**
     * @return False as the side panel does not support keyboard focus
     */
    @Override
    public boolean isFocused() {
        return false;
    }

    /**
     * Sets the focus state of the side panel. Unimplemented as keyboard focus is not required.
     *
     * @param focused True to set focus, false to remove focus
     */
    @Override
    public void setFocused(boolean focused) {/* Unimplemented */}

    /**
     * @return Null as the side panel does not support navigation paths
     */
    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        return null;
    }

    /**
     * @return Null as the side panel does not support navigation focus
     */
    @Override
    public ScreenRect getNavigationFocus() {
        return null;
    }

    /**
     * @return the displayed location's position
     */
    public Vec3d getDisplayedLocationPosition() {
        return displayedLocation != null ? displayedLocation.getPosition() : Vec3d.ZERO;
    }
}