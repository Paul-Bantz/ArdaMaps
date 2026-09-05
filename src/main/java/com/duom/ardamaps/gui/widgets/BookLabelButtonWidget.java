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
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.map.rendering.MaskedIcon;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * A book-label shaped button that protrudes from the edge of the book UI.
 */
public class BookLabelButtonWidget extends AbstractWidget {

    /** Source texture width. */
    private static final int TEX_W = 170;

    /** Source texture height. */
    private static final int TEX_H = 96;

    /** Source width of the left icon cap. */
    private static final int SRC_CAP_LEFT = 96;

    /** Source width of the stretchable centre column. */
    private static final int SRC_CENTRE = 50;

    /** Source width of the right cap. */
    private static final int SRC_CAP_RIGHT = 24;

    /** Source x coordinate of the centre column. */
    private static final int SRC_CENTRE_X = SRC_CAP_LEFT;

    /** Source x coordinate of the right cap. */
    private static final int SRC_CAP_RIGHT_X = SRC_CAP_LEFT + SRC_CENTRE;

    /** Source offset of the icon mask area. */
    private static final int SRC_ICON_OFFSET = 12;

    /** Source size of the icon mask area. */
    private static final int SRC_ICON_SIZE = 72;

    /** Source top edge of the label text band. */
    private static final int SRC_TEXT_TOP = 28;

    /** Source bottom edge of the label text band. */
    private static final int SRC_TEXT_BOTTOM = 68;

    /** Horizontal text padding inside the expanded centre. */
    private static final int TEXT_PADDING = 3;

    /** Duration of the fold/unfold animation in milliseconds. */
    private static final long EXPAND_MS = 100L;

    /** Left mask UV for the visible icon diamond. */
    private static final float MASK_U0 = 12f / 170f;

    /** Top mask UV for the visible icon diamond. */
    private static final float MASK_V0 = 12f / 96f;

    /** Right mask UV for the visible icon diamond. */
    private static final float MASK_U1 = 84f / 170f;

    /** Bottom mask UV for the visible icon diamond. */
    private static final float MASK_V1 = 84f / 96f;

    /** Visual style for the label. */
    private final BookLabelButtonType type;

    /** Behaviour run by this label when activated. */
    private final BookLabelBehaviour behaviour;

    /** Whether this label scroll-selects directly through its dropdown entries. */
    @Getter
    private final boolean directSelection;

    /** Whether an icon was configured for this button. */
    private boolean hasIcon;

    /** Remote http(s) icon URL, or null when the icon is an atlas sprite. */
    @Nullable
    private String iconUrl;

    /** Atlas icon sprite, or null when the icon is remote or unusable. */
    @Nullable
    private Identifier iconSprite;

    /** Explicit icon draw width in pixels, or null to fill the mask area. */
    @Nullable
    private Integer iconWidth;

    /** Explicit icon draw height in pixels, or null to fill the mask area. */
    @Nullable
    private Integer iconHeight;

    /** Fold/unfold animation state. */
    private final ExpansionAnimation expansion = new ExpansionAnimation(EXPAND_MS);

    /** Whether hover-opening is paused until the cursor leaves this label. */
    private boolean hoverSuppressed;

    /** Notified just before this label opens its behaviour. */
    @Setter
    private Consumer<BookLabelButtonWidget> openListener = _ -> {
    };

    /** Gate that decides whether hover may open this label. */
    @Setter
    private HoverGate hoverGate = (_, _, _) -> true;

    /**
     * Creates a new book-label button.
     *
     * @param x          The x screen position.
     * @param y          The y screen position.
     * @param height     The button height.
     * @param type       The visual style.
     * @param behaviour  The activation and popup behaviour.
     * @param text       The button label text.
     * @param icon       The optional atlas sprite path or image URL.
     * @param iconWidth  The optional explicit icon width.
     * @param iconHeight The optional explicit icon height.
     * @param directSelection Whether the label scroll-selects directly through dropdown entries.
     */
    public BookLabelButtonWidget(int x,
                                 int y,
                                 int height,
                                 BookLabelButtonType type,
                                 BookLabelBehaviour behaviour,
                                 Component text,
                                 @Nullable String icon,
                                 @Nullable Integer iconWidth,
                                 @Nullable Integer iconHeight,
                                 boolean directSelection) {

        super(x, y, foldedWidth(height), height,
                text != null ? text : Component.empty());

        this.type = type;
        this.behaviour = behaviour;
        this.directSelection = directSelection;
        setIcon(icon);
        setIconSize(iconWidth, iconHeight);
        this.width = foldedWidth(height);
        this.height = height;
    }

    /**
     * Returns the folded side-map label width for the given height.
     *
     * @param height The destination button height.
     * @return The folded width.
     */
    public static int foldedWidth(int height) {

        return capLeftWidth(height) + capRightWidth(height);
    }

    /**
     * Returns the right cap width for the given height.
     *
     * @param height The destination button height.
     * @return The right cap width.
     */
    public static int capRightWidth(int height) {

        return scaled(SRC_CAP_RIGHT, height);
    }

    /**
     * Sets the icon drawn inside the cap diamond from a sprite path or http(s) URL.
     *
     * @param icon The atlas sprite path or absolute image URL, or null for no icon.
     */
    public void setIcon(@Nullable String icon) {

        hasIcon = icon != null && !icon.isBlank();
        iconUrl = hasIcon && (icon.startsWith("http://") || icon.startsWith("https://")) ? icon : null;
        iconSprite = hasIcon && iconUrl == null ? ModConstants.idOrNull(icon) : null;
    }

    /**
     * Sets the icon draw size inside the cap diamond.
     *
     * @param width  The explicit icon width, or null to fill the mask area.
     * @param height The explicit icon height, or null to fill the mask area.
     */
    public void setIconSize(@Nullable Integer width, @Nullable Integer height) {

        iconWidth = width;
        iconHeight = height;
    }

    /**
     * Renders the book-label button.
     *
     * @param context The GUI draw context.
     * @param mouseX  The current mouse x position.
     * @param mouseY  The current mouse y position.
     * @param delta   The frame delta.
     */
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (!visible) return;

        handleCursor(context);

        boolean hovered = isMouseOver(mouseX, mouseY);

        if (behaviour.opensOnHover()) {

            if (!hovered) hoverSuppressed = false;
            if (hovered && !behaviour.isOpen() && !hoverSuppressed && hoverGate.allowsOpen(this, mouseX, mouseY)) {
                openListener.accept(this);
                behaviour.onActivate(this);
            }
            if (!hovered && behaviour.isOpen()) behaviour.close();
        }

        boolean expand = hovered || behaviour.isOpen();
        expansion.update(expand);

        if (behaviour.tucksBehindLabel() && behaviour.isOpen()) return;

        drawLabel(context);
    }

    /**
     * Draws the label background, icon and text at the current expansion.
     *
     * @param context The GUI draw context.
     */
    private void drawLabel(GuiGraphicsExtractor context) {

        int centreWidth = Math.round(expandedCentreWidth() * expansion.value());
        renderBackground(context, centreWidth);

        renderIcon(context);
        if (expansion.value() > 0f) renderLabel(context);
    }

    /**
     * Checks whether the mouse is over the label or open behaviour content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the label or open content.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        if (!visible) return false;
        if (inLabelRect(mouseX, mouseY)) return true;
        if (isContentMouseOver(mouseX, mouseY)) return true;

        BookLabelBehaviour.ContentBounds bounds = behaviour.openContentBounds();
        return bounds != null && (inContentRect(mouseX, mouseY, bounds) || inBridge(mouseX, mouseY, bounds));
    }

    /**
     * Checks whether the mouse is over this label's open content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over open behaviour content.
     */
    public boolean isContentMouseOver(double mouseX, double mouseY) {

        return visible && behaviour.isOpen() && behaviour.isMouseOver(mouseX, mouseY);
    }

    /**
     * Returns the width currently drawn by the fold animation.
     *
     * @return The current label width.
     */
    private int currentWidth() {

        return capLeftWidth(height) + Math.round(expandedCentreWidth() * expansion.value()) + capRightWidth(height);
    }

    /**
     * Checks whether the mouse is over the currently drawn label.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the label.
     */
    private boolean inLabelRect(double mouseX, double mouseY) {

        return mouseX >= getX() && mouseX <= getX() + currentWidth()
                && mouseY >= getY() && mouseY <= getY() + height;
    }

    /**
     * Checks whether the mouse is over the open content bounds.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param bounds The open content bounds.
     * @return True if the mouse is over open content bounds.
     */
    private boolean inContentRect(double mouseX, double mouseY, BookLabelBehaviour.ContentBounds bounds) {

        return mouseX >= bounds.x() && mouseX <= bounds.x() + bounds.width()
                && mouseY >= bounds.y() && mouseY <= bounds.y() + bounds.height();
    }

    /**
     * Checks whether the mouse is over the bridge from label to open content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param bounds The open content bounds.
     * @return True if the mouse is over the bridge.
     */
    private boolean inBridge(double mouseX, double mouseY, BookLabelBehaviour.ContentBounds bounds) {

        if (bounds.x() <= getX() || mouseX < getX() || mouseX > bounds.x()) return false;

        double t = (mouseX - getX()) / (double) (bounds.x() - getX());
        double top = getY() + t * (bounds.y() - getY());
        double bottom = getY() + height + t * (bounds.y() + bounds.height() - getY() - height);

        return mouseY >= top && mouseY <= bottom;
    }

    /**
     * Checks whether this label currently has open behaviour content.
     *
     * @return True if the behaviour is open.
     */
    public boolean isBehaviourOpen() {

        return visible && behaviour.isOpen();
    }

    /**
     * Renders the button text centered inside the full label.
     *
     * @param context The GUI draw context.
     */
    private void renderLabel(GuiGraphicsExtractor context) {

        var text = getMessage();
        if (text.equals(Component.empty())) return;

        var textRenderer = Client.mc().font;
        int capLeft = capLeftWidth(height);
        int areaLeft = getX() + capLeft;
        int areaWidth = Math.round(expandedCentreWidth() * expansion.value());
        int areaTop = getY() + scaled(SRC_TEXT_TOP, height);
        int areaHeight = scaled(SRC_TEXT_BOTTOM, height) - scaled(SRC_TEXT_TOP, height);
        int textX = areaLeft + (areaWidth - textRenderer.width(text)) / 2;
        int textY = areaTop + (areaHeight - textRenderer.lineHeight) / 2;
        int color = GuiTextures.withAlpha(ModConstants.COLOR_LIGHT_BROWN, expansion.value());

        context.text(textRenderer, text, textX, textY, color, false);
    }

    /**
     * Renders the optional icon inside the cap diamond.
     *
     * @param context The GUI draw context.
     */
    private void renderIcon(GuiGraphicsExtractor context) {

        if (!hasIcon) return;

        int area = scaled(SRC_ICON_SIZE, height);
        int offset = scaled(SRC_ICON_OFFSET, height);
        int drawW = iconWidth == null ? area : Math.min(iconWidth, area);
        int drawH = iconHeight == null ? area : Math.min(iconHeight, area);
        int dx = (area - drawW) / 2;
        int dy = (area - drawH) / 2;
        int x = getX() + offset + dx;
        int y = getY() + offset + dy;
        float maskU0 = maskUv(MASK_U0, MASK_U1, dx, area);
        float maskU1 = maskUv(MASK_U0, MASK_U1, dx + drawW, area);
        float maskV0 = maskUv(MASK_V0, MASK_V1, dy, area);
        float maskV1 = maskUv(MASK_V0, MASK_V1, dy + drawH, area);

        Identifier remote = iconUrl == null ? null : ArdaMapsClient.getHttpImageProvider().getTexture(iconUrl);

        if (remote != null) {

            var provider = ArdaMapsClient.getHttpImageProvider();
            float halfU = crop(provider.getTextureHeight(iconUrl), provider.getTextureWidth(iconUrl));
            float halfV = crop(provider.getTextureWidth(iconUrl), provider.getTextureHeight(iconUrl));

            MaskedIcon.draw(
                    context,
                    remote,
                    .5f - halfU,
                    .5f - halfV,
                    .5f + halfU,
                    .5f + halfV,
                    ModConstants.SIDE_MAP_BUTTON_ICON_MASK,
                    x,
                    y,
                    drawW,
                    drawH,
                    maskU0,
                    maskV0,
                    maskU1,
                    maskV1);
            return;
        }

        Identifier sprite = iconSprite != null ? iconSprite : ModConstants.MAP_ICON;

        MaskedIcon.draw(
                context,
                sprite,
                ModConstants.SIDE_MAP_BUTTON_ICON_MASK,
                x,
                y,
                drawW,
                drawH,
                maskU0,
                maskV0,
                maskU1,
                maskV1);
    }

    /**
     * Maps a pixel offset inside the mask area onto the mask UV range.
     *
     * @param from   The start UV.
     * @param to     The end UV.
     * @param offset The pixel offset inside the mask area.
     * @param area   The mask area size in pixels.
     * @return The mapped UV coordinate.
     */
    private static float maskUv(float from, float to, int offset, int area) {

        return area <= 0 ? from : from + (to - from) * (offset / (float) area);
    }

    /**
     * Returns the half-extent UV needed to centre-crop one texture axis to a square.
     *
     * @param otherAxis The perpendicular texture dimension.
     * @param axis      The texture dimension being cropped.
     * @return The UV half-extent.
     */
    private static float crop(int otherAxis, int axis) {

        if (axis <= 0 || otherAxis <= 0) return .5f;

        return Math.min(axis, otherAxis) / (2f * axis);
    }

    /**
     * Draws the three raw texture columns with only the centre column animated.
     *
     * @param context     The GUI draw context.
     * @param centreWidth The current drawn centre width.
     */
    private void renderBackground(GuiGraphicsExtractor context, int centreWidth) {

        Identifier texture = isExpandedVisual()
                ? type.getHighlightTexture()
                : type.getTexture();
        int color = GuiTextures.tint(active);
        int capLeft = capLeftWidth(height);
        int capRight = capRightWidth(height);
        int x = getX();
        int y = getY();

        GuiTextures.blitScaled(context, texture, x, y, capLeft, height,
                0, 0, SRC_CAP_LEFT, TEX_H, TEX_W, TEX_H, color);
        if (centreWidth > 0) {
            GuiTextures.blitRepeatingScaled(context, texture, x + capLeft, y, centreWidth, height,
                    SRC_CENTRE_X, 0, SRC_CENTRE, TEX_H, scaled(SRC_CENTRE, height),
                    TEX_W, TEX_H, color);
        }
        GuiTextures.blitScaled(context, texture, x + capLeft + centreWidth, y, capRight, height,
                SRC_CAP_RIGHT_X, 0, SRC_CAP_RIGHT, TEX_H, TEX_W, TEX_H, color);
    }

    /**
     * Returns whether the hovered/open visual state should be used.
     *
     * @return Whether the highlighted texture should be drawn.
     */
    private boolean isExpandedVisual() {

        return expansion.isExpanding() || expansion.value() > 0f;
    }

    /**
     * Returns the fully expanded centre width for the current message and height.
     *
     * @return The centre width.
     */
    private int expandedCentreWidth() {

        return Math.max(scaled(SRC_CENTRE, height), Client.mc().font.width(getMessage()) + TEXT_PADDING * 2);
    }

    /**
     * Returns the fully expanded label width.
     *
     * @return The expanded label width.
     */
    public int expandedWidth() {

        return capLeftWidth(height) + expandedCentreWidth() + capRightWidth(height);
    }

    /**
     * Returns the left cap width for the given height.
     *
     * @param height The destination button height.
     * @return The left cap width.
     */
    private static int capLeftWidth(int height) {

        return scaled(SRC_CAP_LEFT, height);
    }

    /**
     * Scales a source-space measurement to the current destination height.
     *
     * @param value  The source-space value.
     * @param height The destination button height.
     * @return The scaled value.
     */
    private static int scaled(int value, int height) {

        return Math.round(value * (height / (float) TEX_H));
    }

    /**
     * Renders open behaviour content in a late overlay pass.
     *
     * @param context The GUI draw context.
     * @param mouseX  The current mouse x position.
     * @param mouseY  The current mouse y position.
     * @param delta   The frame delta.
     */
    public void renderOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (!visible) return;

        behaviour.render(context, mouseX, mouseY, delta);
        if (behaviour.tucksBehindLabel() && behaviour.isOpen()) drawLabel(context);
    }

    /**
     * Handles mouse scrolls over open behaviour content.
     *
     * @param mouseX           The mouse x position.
     * @param mouseY           The mouse y position.
     * @param horizontalAmount The horizontal scroll amount.
     * @param verticalAmount   The vertical scroll amount.
     * @return True if the scroll was handled.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (behaviour.isOpen() && isMouseOver(mouseX, mouseY)) {
            return behaviour.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Routes key presses to open behaviour content.
     *
     * @param event The key event.
     * @return True if the key was handled.
     */
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {

        boolean handled = behaviour.isOpen() && behaviour.keyPressed(event.key());
        if (handled && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            hoverSuppressed = true;
        }
        return handled;
    }

    /**
     * Handles mouse clicks on the label or open behaviour content.
     *
     * @param event       The initiating mouse event.
     * @param doubleClick True if the click is a double click.
     */
    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {

        if (!visible) return;

        double mouseX = event.x();
        double mouseY = event.y();
        if (behaviour.isOpen() && behaviour.isMouseOver(mouseX, mouseY)) {
            if (behaviour.mouseClicked(mouseX, mouseY, event.button()) && behaviour.opensOnHover()) hoverSuppressed = true;
        } else if (behaviour.isOpen() && behaviour.opensOnHover()) {
            if (behaviour.commitHighlighted()) hoverSuppressed = true;
        } else {
            openListener.accept(this);
            behaviour.onActivate(this);
        }

        super.onClick(event, doubleClick);
    }

    /**
     * Closes any content opened by this button's behaviour.
     */
    public void closeBehaviour() {

        behaviour.close();
    }

    /**
     * Collapses this label and closes any opened behaviour content.
     */
    public void collapse() {

        behaviour.close();
        expansion.update(false);
        hoverSuppressed = false;
    }

    /**
     * Appends default button narration.
     *
     * @param builder The narration builder.
     */
    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput builder) {

        defaultButtonNarrationText(builder);
    }

    /**
     * Decides whether a label may hover-open at the current cursor position.
     */
    public interface HoverGate {

        /**
         * Checks whether hover may open the given button.
         *
         * @param button The button considering a hover-open.
         * @param mouseX The mouse x position.
         * @param mouseY The mouse y position.
         * @return True if hover-opening is allowed.
         */
        boolean allowsOpen(BookLabelButtonWidget button, double mouseX, double mouseY);
    }
}
