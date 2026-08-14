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
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * A styled button widget that displays an image.
 * This button type is meant to be displayed on top of the GUI.
 *
 * <p>Each instance is associated with a {@link BookmarkButtonType} that determines
 * the icon and colour scheme used when rendering the button. Supported types are:</p>
 * <ul>
 *   <li>{@link BookmarkButtonType#BOOKMARK_CLOSE} – displays a close/dismiss icon in a reddish tint</li>
 *   <li>{@link BookmarkButtonType#BOOKMARK_CONFIGURATION} – displays a settings icon in a blue tint</li>
 *   <li>{@link BookmarkButtonType#BOOKMARK_GUIDE} – displays a guide/help icon in a blue tint</li>
 *   <li>{@link BookmarkButtonType#BOOKMARK_MAP} – displays a map icon in a green tint</li>
 * </ul>
 *
 * <p>The button tint is only applied when the cursor is <em>not</em> hovering over it;
 * while hovered the icon is rendered at full white (1, 1, 1) to provide a highlight effect.</p>
 */
public class BookmarkButtonWidget extends AbstractWidget {

    /** Runnable executed when the button is clicked. */
    private final Runnable onSelect;

    /**
     * Determines the visual style (icon and tint colour) of the button.
     * Defaults to {@link BookmarkButtonType#BOOKMARK_CLOSE} if {@code null} is supplied
     * to the constructor.
     */
    private final BookmarkButtonType bookmarkButtonType;

    /**
     * Creates a new {@code BookmarkButtonWidget}.
     *
     * @param x                  x screen position (pixels from the left edge of the screen)
     * @param y                  y screen position (pixels from the top edge of the screen)
     * @param w                  width of the button in pixels
     * @param h                  height of the button in pixels
     * @param bookmarkButtonType button style; if {@code null}, defaults to
     *                           {@link BookmarkButtonType#BOOKMARK_CLOSE}
     * @param onClick            runnable invoked when the button is clicked
     */
    public BookmarkButtonWidget(int x,
                                int y,
                                int w,
                                int h,
                                BookmarkButtonType bookmarkButtonType,
                                Runnable onClick) {

        super(x, y,
                w, h,
                bookmarkButtonType.getTranslation());

        this.bookmarkButtonType = bookmarkButtonType;
        this.onSelect = onClick;
        this.width = w;
        this.height = h;
    }

    /**
     * Renders the button widget on the screen.
     *
     * <p>Delegates to the appropriate private render method based on the button's
     * {@link BookmarkButtonType}. If the widget is not {@link #visible}, no rendering
     * is performed.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     * @param delta   time delta (in seconds) since the last render call
     */
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (!visible) return;

        switch (bookmarkButtonType) {
            case BOOKMARK_CLOSE -> renderCloseButton(context, mouseX, mouseY);
            case BOOKMARK_CONFIGURATION -> renderConfigurationButton(context, mouseX, mouseY);
            case BOOKMARK_GUIDE -> renderGuideButton(context, mouseX, mouseY);
            case BOOKMARK_MAP -> renderMapButton(context, mouseX, mouseY);
        }
    }

    /**
     * Renders the close button style.
     *
     * <p>Draws the button background at UV offset (384, 32) within the GUI element
     * atlas and overlays the {@link ModConstants#CLOSE_ICON} sprite in a muted red
     * tint (0.76, 0.42, 0.44) when not hovered.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     */
    private void renderCloseButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        renderBookmarkButton(context, mouseX, mouseY, ModConstants.BOOKMARK_BUTTON_RED_SPRITE, 0.7568f, 0.4235f, 0.4431f, ModConstants.CLOSE_ICON);
    }

    /**
     * Renders the configuration button style.
     *
     * <p>Draws the button background at UV offset (288, 32) within the GUI element
     * atlas and overlays the {@link ModConstants#CONFIGURATION_ICON} sprite in a
     * blue-grey tint (0.53, 0.65, 0.78) when not hovered.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     */
    private void renderConfigurationButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        renderBookmarkButton(context, mouseX, mouseY, ModConstants.BOOKMARK_BUTTON_BLUE_SPRITE, 0.5333f, 0.6549f, 0.7765f, ModConstants.CONFIGURATION_ICON);
    }

    /**
     * Renders the guide button style.
     *
     * <p>Draws the button background at UV offset (384, 128) within the GUI element
     * atlas and overlays the {@link ModConstants#GUIDE_ICON} sprite in a muted purple
     * tint (0.61, 0.49, 0.65) when not hovered.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     */
    private void renderGuideButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        renderBookmarkButton(context, mouseX, mouseY, ModConstants.BOOKMARK_BUTTON_VIOLET_SPRITE, 0.611f, 0.494f, 0.647f, ModConstants.GUIDE_ICON);
    }

    /**
     * Renders the map button style.
     *
     * <p>Draws the button background at UV offset (288, 128) within the GUI element
     * atlas and overlays the {@link ModConstants#MAP_ICON} sprite in a muted green
     * tint (0.38, 0.51, 0.35) when not hovered.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     */
    private void renderMapButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        renderBookmarkButton(context, mouseX, mouseY, ModConstants.BOOKMARK_BUTTON_GREEN_SPRITE, 0.3843f, 0.5058f, 0.3490f, ModConstants.MAP_ICON);
    }

    /**
     * Core rendering routine shared by all bookmark button styles.
     *
     * <p>Performs two draw operations: draws the bookmark background sprite stretched to fill
     * the button's bounds, then draws the supplied {@code icon} sprite centred inside the
     * button. The icon is sized to 50 % of the shorter button dimension. When the cursor is
     * <em>not</em> hovering over the button the icon is drawn with tint ({@code r}, {@code g},
     * {@code b}, 1.0); otherwise the icon renders at full white to produce a hover-highlight
     * effect.</p>
     *
     * @param context the {@link GuiGraphicsExtractor} used for all draw calls
     * @param mouseX  current x position of the mouse cursor in screen pixels
     * @param mouseY  current y position of the mouse cursor in screen pixels
     * @param sprite  {@link Identifier} of the bookmark background sprite
     * @param r       red component of the icon tint applied when not hovered (0.0–1.0)
     * @param g       green component of the icon tint applied when not hovered (0.0–1.0)
     * @param b       blue component of the icon tint applied when not hovered (0.0–1.0)
     * @param icon    {@link Identifier} of the sprite to retrieve from {@link IconSpriteAtlas}
     *                and render as the button icon
     */
    private void renderBookmarkButton(GuiGraphicsExtractor context, int mouseX, int mouseY, Identifier sprite, float r, float g, float b, Identifier icon) {

        var x = getX();
        var y = getY();

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, getWidth(), getHeight());

        var iconSize = (int) (Math.min(getWidth(), getHeight()) * .5);
        var halfIconSize = (int) (iconSize / 2f);

        int color = isMouseOver(mouseX, mouseY) || isFocused()
                ? ModConstants.COLOR_WHITE
                : GuiTextures.argb(r, g, b, 1.0f);

        context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(icon),
                x + halfIconSize, y + halfIconSize, iconSize, iconSize, color);

    }

    /**
     * Handles mouse click events on the button.
     *
     * <p>Executes the {@link #onSelect} runnable and then delegates to
     * {@link AbstractWidget#onClick(MouseButtonEvent, boolean)} for default click processing
     * (e.g. playing the click sound). If the widget is not {@link #visible} the
     * click is silently ignored.</p>
     *
     * @param event       the initiating mouse event
     * @param doubleClick if this is a double click or not
     */
    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {

        if (!visible) return;

        onSelect.run();
        super.onClick(event, doubleClick);
    }

    /**
     * Appends narration messages for accessibility.
     *
     * <p>Delegates to {@link #defaultButtonNarrationText(NarrationElementOutput)} so
     * that screen readers receive the widget's default title and usage hint text.</p>
     *
     * @param builder the {@link NarrationElementOutput} to append messages to
     */
    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
