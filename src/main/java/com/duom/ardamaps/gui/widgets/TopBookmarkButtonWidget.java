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

import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * A top bookmark tab that unfolds downward from the book UI.
 */
public class TopBookmarkButtonWidget extends AbstractWidget {

    /** Source texture width. */
    private static final int TEX_W = 96;

    /** Source texture height. */
    private static final int TEX_H = 170;

    /** Source height of the top icon cap. */
    private static final int SRC_CAP_TOP = 96;

    /** Source height of the stretchable centre band. */
    private static final int SRC_CENTRE = 50;

    /** Source height of the bottom tail cap. */
    private static final int SRC_CAP_BOTTOM = 24;

    /** Source y coordinate of the centre band. */
    private static final int SRC_CENTRE_Y = SRC_CAP_TOP;

    /** Source y coordinate of the bottom cap. */
    private static final int SRC_CAP_BOTTOM_Y = SRC_CAP_TOP + SRC_CENTRE;

    /** Source offset of the icon box. */
    private static final int SRC_ICON_BOX_OFFSET = 12;

    /** Source size of the icon box. */
    private static final int SRC_ICON_BOX_SIZE = 72;

    /** Default source-space icon size. */
    public static final int DEFAULT_SRC_ICON_SIZE = SRC_ICON_BOX_SIZE;

    /** Duration of the fold/unfold animation in milliseconds. */
    private static final long EXPAND_MS = 100L;

    /** Visual style for the bookmark. */
    private final TopBookmarkButtonType type;

    /** Runnable executed when the button is clicked. */
    private final Runnable onSelect;

    /** Source-space icon size clamped to the cap icon box. */
    private int srcIconSize = DEFAULT_SRC_ICON_SIZE;

    /** Fold/unfold animation state. */
    private final ExpansionAnimation expansion = new ExpansionAnimation(EXPAND_MS);

    /** Whether the expansion state still needs seeding from the initial hover state. */
    private boolean needsExpansionSeed = true;

    /**
     * Creates a new top bookmark button.
     *
     * @param x       The x screen position.
     * @param y       The y screen position.
     * @param width   The button width.
     * @param type    The visual style.
     * @param onClick Runnable invoked when the button is clicked.
     */
    public TopBookmarkButtonWidget(int x, int y, int width, TopBookmarkButtonType type, Runnable onClick) {

        super(x, y, width, foldedHeight(width), type.getTranslation());

        this.type = type;
        this.onSelect = onClick;
        this.width = width;
        this.height = foldedHeight(width);
    }

    /**
     * Returns the folded top bookmark height for the given width.
     *
     * @param width The destination button width.
     * @return The folded height.
     */
    public static int foldedHeight(int width) {

        return scaled(SRC_CAP_TOP, width) + scaled(SRC_CAP_BOTTOM, width);
    }

    /**
     * Sets the icon size in source-texture units.
     *
     * @param sourceSize The icon size relative to the 96 px source cap.
     */
    public void setIconSize(int sourceSize) {

        srcIconSize = Math.max(1, Math.min(sourceSize, SRC_ICON_BOX_SIZE));
    }

    /**
     * Renders the top bookmark button.
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
        if (needsExpansionSeed) {
            needsExpansionSeed = false;
            expansion.snap(hovered);
        }

        float amount = expansion.update(hovered);
        int centreHeight = Math.round(scaled(SRC_CENTRE, width) * amount);
        renderBackground(context, hovered, centreHeight);
        renderIcon(context);
    }

    /**
     * Draws the three raw texture bands with only the centre band animated.
     *
     * @param context      The GUI draw context.
     * @param hovered      Whether the mouse is over the button.
     * @param centreHeight The current drawn centre height.
     */
    private void renderBackground(GuiGraphicsExtractor context, boolean hovered, int centreHeight) {

        Identifier texture = hovered || isFocused()
                ? type.getHighlightTexture()
                : type.getTexture();
        int color = GuiTextures.tint(active);
        int capTop = scaled(SRC_CAP_TOP, width);
        int capBottom = scaled(SRC_CAP_BOTTOM, width);
        int x = getX();
        int y = getY();

        GuiTextures.blitScaled(context, texture, x, y, width, capTop,
                0, 0, TEX_W, SRC_CAP_TOP, TEX_W, TEX_H, color);
        if (centreHeight > 0) {
            GuiTextures.blitRepeatingScaledVertical(context, texture, x, y + capTop, width, centreHeight,
                    0, SRC_CENTRE_Y, TEX_W, SRC_CENTRE, scaled(SRC_CENTRE, width),
                    TEX_W, TEX_H, color);
        }
        GuiTextures.blitScaled(context, texture, x, y + capTop + centreHeight, width, capBottom,
                0, SRC_CAP_BOTTOM_Y, TEX_W, SRC_CAP_BOTTOM, TEX_W, TEX_H, color);
    }

    /**
     * Renders the icon inside the top cap.
     *
     * @param context The GUI draw context.
     */
    private void renderIcon(GuiGraphicsExtractor context) {

        int box = scaled(SRC_ICON_BOX_SIZE, width);
        int iconSize = scaled(srcIconSize, width);
        int offset = scaled(SRC_ICON_BOX_OFFSET, width) + (box - iconSize) / 2;

        context.blitSprite(RenderPipelines.GUI_TEXTURED,
                IconSpriteAtlas.retrieveSprite(type.getIcon()),
                getX() + offset,
                getY() + offset,
                iconSize,
                iconSize);
    }

    /**
     * Scales a source-space measurement to the current destination width.
     *
     * @param value The source-space value.
     * @param width The destination button width.
     * @return The scaled value.
     */
    private static int scaled(int value, int width) {

        return Math.round(value * (width / (float) TEX_W));
    }

    /**
     * Handles mouse click events on the button.
     *
     * @param event       The initiating mouse event.
     * @param doubleClick If this is a double click or not.
     */
    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {

        if (!visible) return;

        onSelect.run();
        super.onClick(event, doubleClick);
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
}
