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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.core.data.conversion.ContentBlock;
import com.duom.ardamaps.core.data.guide.GuideImageCache;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility for rendering a {@link List} of {@link ContentBlock}s viewport.
 *
 * <h3>Supported block types</h3>
 * <ul>
 *   <li>{@link ContentBlock.TextBlock} – wrapped text, drawn line-by-line.</li>
 *   <li>{@link ContentBlock.ImageBlock} – texture drawn at the declared size and alignment.</li>
 *   <li>{@link ContentBlock.ListBlock} – bullet list; each item gets a filled 3×3 blue square
 *       followed by indented wrapped text.</li>
 * </ul>
 *
 * <p>Layout is expensive: text wrapping and per-glyph width measurement are computed once per
 * {@code (blocks, wrapWidth)} pair and cached. The cache is invalidated when either the blocks
 * list reference or the wrap width changes.</p>
 *
 * <p>The caller is responsible for setting up and tearing down the scissor region so that the
 * returned {@link RenderResult} can be used to draw hover tooltips <em>after</em> the scissor
 * is disabled.</p>
 */
public final class TextContentBlockRenderer {

    /** Horizontal size (px) of the rendered bullet square for {@link ContentBlock.ListBlock}. */
    private static final int BULLET_SIZE = 3;

    /** Gap (px) between the bullet square and the list-item text. */
    private static final int BULLET_GAP = 5;

    private static final int LINE_GAP = 1;

    /** Total horizontal indent per list item (bullet + gap). */
    private static final int LIST_INDENT = BULLET_SIZE + BULLET_GAP;

    private final TextRenderer textRenderer;
    private final int defaultColor;

    /**
     * Pre-measured data for a single glyph in a wrapped line.
     *
     * @param relX  x-offset from the start of the line (px)
     * @param width advance width of this glyph (px)
     * @param type  run type: identified via the sentinel font {@link Identifier} embedded in the style
     *              by {@code HtmlConverter} ({@link ModConstants#RUN_FONT_CHATCOMMAND} /
     *              {@link ModConstants#RUN_FONT_KEYBIND})
     * @param style the {@link Style} of this glyph (carries insertion text for keybind labels)
     */
    private record GlyphRun(int relX, int width, int type, Style style) {}

    /** An {@link OrderedText} line together with its pre-parsed {@link GlyphRun} list. */
    private record LineLayout(OrderedText line, List<GlyphRun> glyphRuns) {}

    /** Sealed hierarchy mirroring {@link ContentBlock} – one entry per block in the layout cache. */
    private sealed interface BlockLayout permits BlockquoteLayout, ImageLayout, LineBreakLayout, ListLayout, TextLayout, TitleLayout {}

    /** Cached layout for a {@link ContentBlock.TextBlock}. */
    private record TextLayout(List<LineLayout> lines, int height) implements BlockLayout {}

    /** Cached layout for a {@link ContentBlock.TitleBlock}*/
    private record TitleLayout(Text line, float scale, int height) implements BlockLayout {}

    /**
     * Cached layout for a {@link ContentBlock.ImageBlock}.
     *
     * @param block        the source image block
     * @param imageHeight  the (possibly scaled) pixel height of the rendered image
     * @param captionLines pre-wrapped ordered text lines for the caption (empty = no caption)
     * @param height       total pixel height: image + optional caption gap + caption lines
     */
    private record ImageLayout(ContentBlock.ImageBlock block, int imageHeight,
                               List<OrderedText> captionLines, int height) implements BlockLayout {}

    /**
     * Cached layout for a {@link ContentBlock.ListBlock}.
     * Each element of {@code items} is the list of wrapped {@link LineLayout}s for one {@code <li>}.
     */
    private record ListLayout(List<List<LineLayout>> items, int height) implements BlockLayout {}

    /**
     * Cached layout for a {@link ContentBlock.BlockquoteBlock}.
     *
     * <p>The {@code height} includes {@code ⌈fontHeight/2⌉} px of top and bottom margin so that
     * surrounding blocks need no special awareness of blockquote spacing.</p>
     */
    private record BlockquoteLayout(List<LineLayout> lines, int height) implements BlockLayout {}

    /**
     * Cached layout for a {@link ContentBlock.LineBreakBlock}.
     * Advances {@code totalHeight} by one line height without drawing anything.
     */
    private record LineBreakLayout(int height) implements BlockLayout {}

    /** The {@code wrapWidth} that produced {@link #cachedLayouts}; {@code -1} = no cache. */
    private int cachedWrapWidth = -1;

    /** Reference identity of the blocks list that produced {@link #cachedLayouts}. */
    private List<ContentBlock> cachedBlocks = null;

    /** Pre-computed layout for every entry in {@link #cachedBlocks}. */
    private List<BlockLayout> cachedLayouts = null;

    /**
     * Creates a new text block renderer.
     *
     * @param textRenderer     the underlying text renderer
     * @param defaultTextColor default text colour
     */
    public TextContentBlockRenderer(TextRenderer textRenderer, int defaultTextColor) {
        this.textRenderer = textRenderer;
        this.defaultColor = defaultTextColor;
    }

    /**
     * (Re-)builds the layout cache for {@code blocks} at {@code wrapWidth}.
     * Called automatically by {@link #render} when the cache is stale.
     *
     * <p>Performs all expensive work that depends on {@code wrapWidth}:</p>
     * <ul>
     *   <li>Text wrapping via {@link TextRenderer#wrapLines}.</li>
     *   <li>Per-glyph width measurement via {@link #parseGlyphRuns} (O(n) per line).</li>
     * </ul>
     */
    private void buildLayouts(List<ContentBlock> blocks, int wrapWidth) {

        int fontHeight    = textRenderer.fontHeight;
        int itemWrapWidth = wrapWidth - LIST_INDENT;

        List<BlockLayout> layouts = new ArrayList<>(blocks.size());

        for (ContentBlock block : blocks) {

            if (block instanceof ContentBlock.TextBlock textBlock) {

                addContentBlockLayout(wrapWidth, textBlock, fontHeight, layouts);

            } else if (block instanceof ContentBlock.ImageBlock imgBlock) {

                addImageBlockLayout(wrapWidth, imgBlock, fontHeight, layouts);

            } else if (block instanceof ContentBlock.ListBlock listBlock) {

                addListBlockLayout(listBlock, itemWrapWidth, fontHeight, layouts);

            } else if (block instanceof ContentBlock.BlockquoteBlock bqBlock) {

                addBlockQuoteLayout(wrapWidth, bqBlock, fontHeight, layouts);

            } else if (block instanceof ContentBlock.LineBreakBlock) {

                layouts.add(new LineBreakLayout(fontHeight + LINE_GAP));

            } else if (block instanceof ContentBlock.SeparatorBlock) {

                layouts.add(new LineBreakLayout(fontHeight/2));

            } else if (block instanceof ContentBlock.TitleBlock titleBlock) {
                addTitleBlockLayout(titleBlock, fontHeight, layouts);
            }
        }

        cachedLayouts   = layouts;
        cachedBlocks    = blocks;
        cachedWrapWidth = wrapWidth;
    }

    private void addBlockQuoteLayout(int wrapWidth, ContentBlock.BlockquoteBlock bqBlock, int fontHeight, List<BlockLayout> layouts) {
        int bqWrapWidth = wrapWidth - ModConstants.BLOCKQUOTE_INDENT;
        List<OrderedText> wrappedLines = textRenderer.wrapLines(bqBlock.text(), bqWrapWidth);
        List<LineLayout>  lineLayouts  = new ArrayList<>(wrappedLines.size());

        for (OrderedText line : wrappedLines) {
            lineLayouts.add(new LineLayout(line, parseGlyphRuns(line)));
        }

        int height   = wrappedLines.size() * fontHeight;
        layouts.add(new BlockquoteLayout(lineLayouts, height));
    }

    private void addListBlockLayout(ContentBlock.ListBlock listBlock, int itemWrapWidth, int fontHeight, List<BlockLayout> layouts) {
        List<List<LineLayout>> itemLayouts = new ArrayList<>(listBlock.items().size());
        int blockHeight = 0;

        for (var item : listBlock.items()) {

            List<OrderedText> wrappedLines = textRenderer.wrapLines(item, itemWrapWidth);
            List<LineLayout>  lineLayouts  = new ArrayList<>(wrappedLines.size());

            for (OrderedText line : wrappedLines) {
                lineLayouts.add(new LineLayout(line, parseGlyphRuns(line)));
            }

            itemLayouts.add(lineLayouts);
            blockHeight += wrappedLines.size() * (fontHeight + LINE_GAP) + 1; // +1 inter-item gap
        }

        layouts.add(new ListLayout(itemLayouts, blockHeight));
    }

    private void addImageBlockLayout(int wrapWidth, ContentBlock.ImageBlock imgBlock, int fontHeight, List<BlockLayout> layouts) {
        // Caption wrapping
        List<OrderedText> captionLines = List.of();
        int captionHeight = 0;
        if (imgBlock.caption() != null && !imgBlock.caption().isBlank()) {
            net.minecraft.text.MutableText captionText = net.minecraft.text.Text.literal(imgBlock.caption())
                    .styled(s -> s.withItalic(true));
            captionLines  = textRenderer.wrapLines(captionText, wrapWidth);
            captionHeight = 2 + captionLines.size() * (fontHeight + LINE_GAP); // 2 px gap above caption
        }

        layouts.add(new ImageLayout(imgBlock, imgBlock.height(), captionLines, imgBlock.height() + captionHeight));
    }

    private void addContentBlockLayout(int wrapWidth, ContentBlock.TextBlock textBlock, int fontHeight, List<BlockLayout> layouts) {
        List<OrderedText> wrappedLines = textRenderer.wrapLines(textBlock.text(), wrapWidth);
        List<LineLayout>  lineLayouts  = new ArrayList<>(wrappedLines.size());

        for (OrderedText line : wrappedLines) {
            lineLayouts.add(new LineLayout(line, parseGlyphRuns(line)));
        }

        int height = wrappedLines.size() * (fontHeight + LINE_GAP);
        layouts.add(new TextLayout(lineLayouts, height));
    }


    private void addTitleBlockLayout(ContentBlock.TitleBlock titleBlock, int fontHeight, List<BlockLayout> layouts) {

        Text title = Text.literal(titleBlock.title()).styled(s -> s.withColor(titleBlock.type().getColor()));

        float scale = titleBlock.type().getScale();
        int height = (int) (fontHeight * scale);

        layouts.add(new TitleLayout(title, scale, height));
    }

    /**
     * Parses a single {@link OrderedText} line into a list of {@link GlyphRun}s.
     *
     * <p>Uses the same prefix-width approach as the original renderer: for each glyph,
     * {@link TextRenderer#getWidth} is called on the accumulated prefix string, giving the
     * exact pixel x-offset that Minecraft would compute for that character. This is O(n²)
     * per line but is only ever called at cache-build time (once per {@code wrapWidth}
     * change), never during rendering.</p>
     *
     * <p>A naïve running accumulator (adding single-char widths) drifts due to
     * integer-truncation of fractional glyph advances (e.g. spaces in Minecraft's Unicode
     * font), causing backgrounds to shift right proportionally to preceding whitespace.</p>
     *
     * @param line the line to parse
     * @return ordered list of glyph runs (one per code-point in {@code line})
     */
    private List<GlyphRun> parseGlyphRuns(OrderedText line) {

        List<GlyphRun> runs = new ArrayList<>();
        StringBuilder seen = new StringBuilder();

        line.accept((index, style, codePoint) -> {
            String charStr = new String(Character.toChars(codePoint));
            int xBefore = textRenderer.getWidth(seen.toString());
            seen.append(charStr);
            int xAfter  = textRenderer.getWidth(seen.toString());
            int width   = xAfter - xBefore;
            runs.add(new GlyphRun(xBefore, width, detectSpecialRunType(style), style));
            return true;
        });

        return runs;
    }

    /**
     * Renders all {@code blocks} into the given viewport.
     *
     * <p>Layout is recomputed only when {@code wrapWidth} or the {@code blocks} list reference
     * changes. Visibility culling ({@code topY}…{@code bottomY}) and scroll are applied per
     * frame without touching the cache.</p>
     *
     * @param context      draw context for the current frame
     * @param blocks       content blocks to render (must not be {@code null})
     * @param topX         left edge X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param wrapWidth    pixel width available for text wrapping
     * @param bottomY      bottom edge Y of the visible viewport
     * @param scrollOffset current vertical scroll in pixels (subtracted from Y per line)
     * @param mouseX       current mouse X (used for hover-style detection)
     * @param mouseY       current mouse Y (used for hover-style detection)
     * @return a {@link RenderResult} carrying the total pixel height of all rendered content
     * and the {@link Style} under the mouse cursor (or {@code null} if none)
     */
    public RenderResult render(
            DrawContext context,
            List<ContentBlock> blocks,
            int topX,
            int topY,
            int wrapWidth,
            int bottomY,
            int scrollOffset,
            int mouseX,
            int mouseY) {

        // Rebuild the layout cache only when blocks reference or wrap width has changed.
        if (wrapWidth != cachedWrapWidth || blocks != cachedBlocks) {
            buildLayouts(blocks, wrapWidth);
        }

        int startY = topY - scrollOffset;
        RenderResult renderResult = new RenderResult();

        for (BlockLayout layout : cachedLayouts) {

            if (layout instanceof TextLayout textLayout) {

                renderTextLayout(context, textLayout, topX, topY, startY, bottomY, mouseX, mouseY, renderResult);

            } else if (layout instanceof ImageLayout imageLayout) {

                renderImageLayout(context, imageLayout, wrapWidth, topX, topY, startY, bottomY, renderResult);

            } else if (layout instanceof ListLayout listLayout) {

                renderListLayout(context, listLayout, topX, topY, startY, bottomY, mouseX, mouseY, renderResult);

            } else if (layout instanceof BlockquoteLayout blockquoteLayout) {

                renderBlockquoteLayout(context, blockquoteLayout, topX, topY, startY, bottomY, mouseX, mouseY, renderResult);

            } else if (layout instanceof LineBreakLayout lineBreakLayout) {

                renderResult.totalHeight += lineBreakLayout.height();
            } else if (layout instanceof TitleLayout titleLayout) {

                renderTitleLayout(context, titleLayout, topX, topY, startY, bottomY, renderResult);
            }
        }

        return renderResult;
    }

    /**
     * Renders a cached {@link TextLayout}.
     *
     * @param context      the draw context
     * @param layout       the cached text layout
     * @param topX         top-left X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param startY       Y offset to start drawing the content (topY − scrollOffset)
     * @param bottomY      bottom Y of the viewport
     * @param mouseX       mouse X coordinate
     * @param mouseY       mouse Y coordinate
     * @param renderResult the rendering result
     */
    private void renderTextLayout(DrawContext context,
                                  TextLayout layout,
                                  int topX, int topY, int startY, int bottomY,
                                  int mouseX, int mouseY,
                                  RenderResult renderResult) {

        int fontHeight = textRenderer.fontHeight;

        for (LineLayout lineLayout : layout.lines()) {

            int drawY = startY + renderResult.totalHeight;

            if (drawY + fontHeight >= topY && drawY <= bottomY) {
                drawLine(context, lineLayout, topX, drawY, renderResult, mouseX, mouseY, fontHeight);
            }

            renderResult.totalHeight += fontHeight + LINE_GAP;
        }
    }

    /**
     * Renders a cached {@link TitleLayout}.
     *
     * @param context      the draw context
     * @param layout       the cached text layout
     * @param topX         top-left X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param startY       Y offset to start drawing the content (topY − scrollOffset)
     * @param bottomY      bottom Y of the viewport
     * @param renderResult the rendering result
     */
    private void renderTitleLayout(DrawContext context,
                                  TitleLayout layout,
                                  int topX, int topY, int startY, int bottomY,
                                  RenderResult renderResult) {

        int titleHeight = (int)(textRenderer.fontHeight * layout.scale);
        int drawY = startY + renderResult.totalHeight;

        if (drawY + titleHeight >= topY && drawY <= bottomY) {

            context.getMatrices().push();
            context.getMatrices().translate(topX, drawY, 0);
            context.getMatrices().scale(layout.scale, layout.scale, 1.0f);

            context.drawText(
                    textRenderer,
                    layout.line,
                    0,
                    0,
                    defaultColor,
                    false
            );

            context.getMatrices().pop();
        }

        renderResult.totalHeight += titleHeight;
    }

    /**
     * Renders a cached {@link ImageLayout}.
     *
     * @param context      the draw context
     * @param layout       the cached image layout
     * @param wrapWidth    pixel width of the content area (for alignment)
     * @param topX         top-left X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param startY       Y offset to start drawing the content
     * @param bottomY      bottom Y of the viewport
     * @param renderResult the rendering result
     */
    private void renderImageLayout(DrawContext context,
                                   ImageLayout layout,
                                   int wrapWidth,
                                   int topX, int topY, int startY, int bottomY,
                                   RenderResult renderResult) {

        ContentBlock.ImageBlock imgBlock = layout.block();
        int drawY = startY + renderResult.totalHeight;

        var imageWidth  = Math.min(imgBlock.width(), wrapWidth);
        var imageHeight = layout.imageHeight();

        if (imgBlock.width() > imageWidth) {
            imageHeight = (int) ((double) imgBlock.height() * imageWidth / imgBlock.width());
        }

        if (drawY + imageHeight >= topY && drawY <= bottomY) {

            Identifier texture = GuideImageCache.getTexture(imgBlock.src());

            if (texture != null) {
                int imgX = switch (imgBlock.align()) {
                    case LEFT   -> topX;
                    case CENTER -> topX + (wrapWidth - imageWidth) / 2;
                    case RIGHT  -> topX + wrapWidth - imageWidth;
                };
                context.drawTexture(texture,
                        imgX, drawY,
                        0, 0,
                        imageWidth, imageHeight,
                        imageWidth, imageHeight);
            }
        }

        renderResult.totalHeight += imageHeight;

        // Render caption lines, if any
        if (!layout.captionLines().isEmpty()) {

            int fontHeight = textRenderer.fontHeight;
            renderResult.totalHeight += 2; // gap between image and caption

            for (OrderedText captionLine : layout.captionLines()) {

                int captionDrawY = startY + renderResult.totalHeight;

                if (captionDrawY + fontHeight >= topY && captionDrawY <= bottomY) {
                    int lineWidth = textRenderer.getWidth(captionLine);
                    int captionX  = topX + (wrapWidth - lineWidth) / 2;
                    context.drawText(textRenderer, captionLine, captionX, captionDrawY + LINE_GAP,
                            ModConstants.COLOR_DARK_BROWN, false);
                }

                renderResult.totalHeight += fontHeight + LINE_GAP;
            }
        }
    }

    /**
     * Renders a cached {@link ListLayout}.
     *
     * @param context      the draw context
     * @param layout       the cached list layout
     * @param topX         top-left X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param startY       Y offset to start drawing the content
     * @param bottomY      bottom Y of the viewport
     * @param mouseX       mouse X coordinate
     * @param mouseY       mouse Y coordinate
     * @param renderResult the rendering result
     */
    private void renderListLayout(DrawContext context,
                                  ListLayout layout,
                                  int topX, int topY, int startY, int bottomY,
                                  int mouseX, int mouseY,
                                  RenderResult renderResult) {

        int fontHeight = textRenderer.fontHeight;

        for (List<LineLayout> itemLines : layout.items()) {

            // Bullet on the first line only, vertically centred
            int firstDrawY = startY + renderResult.totalHeight;

            if (firstDrawY + fontHeight >= topY && firstDrawY <= bottomY) {
                int bulletY = firstDrawY + (fontHeight - BULLET_SIZE) / 2;
                context.fill(topX, bulletY,
                        topX + BULLET_SIZE, bulletY + BULLET_SIZE,
                        ModConstants.COLOR_BLUE | 0xFF000000);
            }

            for (LineLayout lineLayout : itemLines) {

                int drawY = startY + renderResult.totalHeight;

                if (drawY + fontHeight >= topY && drawY <= bottomY) {
                    drawLine(context, lineLayout, topX + LIST_INDENT, drawY, renderResult, mouseX, mouseY, fontHeight);
                }

                renderResult.totalHeight += fontHeight + LINE_GAP;
            }

            renderResult.totalHeight += 1; // inter-item gap
        }
    }

    /**
     * Renders a cached {@link BlockquoteLayout}.
     *
     * <p>Draws {@code ⌈fontHeight/2⌉} px of top margin, a
     * {@link ModConstants#BLOCKQUOTE_ACCENT_WIDTH}-px wide medium-blue vertical bar alongside
     * the content lines, the lines themselves indented by {@link ModConstants#BLOCKQUOTE_INDENT}
     * px and coloured medium-blue, then {@code ⌈fontHeight/2⌉} px of bottom margin.</p>
     *
     * @param context      the draw context
     * @param layout       the cached blockquote layout
     * @param topX         top-left X of the content area in screen coordinates
     * @param topY         top edge Y of the visible viewport in screen coordinates
     * @param startY       Y offset to start drawing the content (topY − scrollOffset)
     * @param bottomY      bottom Y of the viewport
     * @param mouseX       mouse X coordinate
     * @param mouseY       mouse Y coordinate
     * @param renderResult the rendering result
     */
    private void renderBlockquoteLayout(DrawContext context,
                                        BlockquoteLayout layout,
                                        int topX, int topY, int startY, int bottomY,
                                        int mouseX, int mouseY,
                                        RenderResult renderResult) {

        int fontHeight = textRenderer.fontHeight;
        int bqColor    = ModConstants.COLOR_BLUE | 0xFF000000;

        // Accent bar – spans the full pixel height of the content lines
        int contentHeight  = layout.lines().size() * (fontHeight + LINE_GAP);
        int accentTop      = startY + renderResult.totalHeight;
        int accentBottom   = accentTop + contentHeight;
        int clampedTop     = Math.max(accentTop, topY);
        int clampedBottom  = Math.min(accentBottom, bottomY);
        if (clampedTop < clampedBottom) {
            context.fill(topX, clampedTop,
                         topX + ModConstants.BLOCKQUOTE_ACCENT_WIDTH, clampedBottom,
                         bqColor);
        }

        // Render lines in medium-blue, indented past the accent bar
        int textX = topX + ModConstants.BLOCKQUOTE_INDENT;
        for (LineLayout lineLayout : layout.lines()) {

            int drawY = startY + renderResult.totalHeight;

            if (drawY + fontHeight >= topY && drawY <= bottomY) {
                drawSpecialRunBackgrounds(context, lineLayout.glyphRuns(), textX, drawY);
                context.drawText(textRenderer, lineLayout.line(), textX, drawY + LINE_GAP, bqColor, false);
                renderResult.hoveredStyle = pickHoverStyle(mouseX, mouseY, fontHeight,
                        lineLayout.line(), drawY, textX, renderResult.hoveredStyle);
            }

            renderResult.totalHeight += fontHeight + LINE_GAP;
        }
    }

    /**
     * Renders a single pre-parsed line: special-run backgrounds, the text itself, and hover detection.
     *
     * @param context      the draw context
     * @param lineLayout   the pre-parsed line (ordered text + glyph runs)
     * @param x            left edge X coordinate
     * @param y            top edge Y coordinate
     * @param renderResult the render result (hover style is updated in-place)
     * @param mouseX       mouse X coordinate
     * @param mouseY       mouse Y coordinate
     * @param fontHeight   the font height
     */
    private void drawLine(DrawContext context, LineLayout lineLayout, int x, int y,
                          RenderResult renderResult, int mouseX, int mouseY, int fontHeight) {

        drawSpecialRunBackgrounds(context, lineLayout.glyphRuns(), x, y);

        context.drawText(textRenderer, lineLayout.line(), x, y + LINE_GAP, defaultColor, false);

        renderResult.hoveredStyle = pickHoverStyle(mouseX, mouseY, fontHeight,
                lineLayout.line(), y, x, renderResult.hoveredStyle);
    }

    /**
     * Paints background rectangles for {@code <chatcommand>} and {@code <keybind>} inline runs
     * using the pre-parsed {@link GlyphRun} list – no glyph walking or width measurement at render time.
     *
     * @param context    draw context
     * @param glyphRuns  pre-parsed glyph runs for this line (from {@link #parseGlyphRuns})
     * @param startX     left edge X of the text line in screen coordinates
     * @param drawY      top edge Y of the text line in screen coordinates
     */
    private void drawSpecialRunBackgrounds(DrawContext context,
                                           List<GlyphRun> glyphRuns,
                                           int startX,
                                           int drawY) {

        if (glyphRuns.isEmpty()) return;

        int fontHeight = textRenderer.fontHeight;
        int padding    = ModConstants.COMMAND_PADDING;

        int i = 0;
        while (i < glyphRuns.size()) {

            GlyphRun g = glyphRuns.get(i);
            if (g.type() == 0) { i++; continue; }

            int type     = g.type();
            int runRelX  = g.relX();
            int j        = i;
            while (j < glyphRuns.size() && glyphRuns.get(j).type() == type) j++;

            GlyphRun last     = glyphRuns.get(j - 1);
            int runEndRelX    = last.relX() + last.width();

            int backgroundX    = startX + runRelX   - padding * 2;
            int backgroundEndX = startX + runEndRelX + padding * 2;
            int backgroundY    = drawY  - padding + LINE_GAP + 1;
            int backgroundEndY = drawY  + fontHeight + padding + LINE_GAP;

            if (type == 1) {

                // chatcommand: solid dark background
                context.fill(backgroundX, backgroundY, backgroundEndX, backgroundEndY, ModConstants.COMMAND_BG_COLOR);

            } else if (type == 2) {

                // Retrieve the key label stored in Style.insertion by HtmlConverter.buildKeybindText.
                // NOTE: insertion is repurposed to carry the display label; guide text is never
                // processed by the chat input system so the field's normal function is not triggered.
                String label = glyphRuns.get(i).style().getInsertion();
                if (label == null || label.isBlank()) label = "?";

                int labelWidth = textRenderer.getWidth(label);
                int faceWidth  = backgroundEndX - backgroundX;
                int faceHeight = backgroundEndY - backgroundY;
                int keycapY    = backgroundY - textRenderer.fontHeight / 2;

                // Key-cap face (three drawTexture slices: left cap, centre stretch, right cap)
                context.drawTexture(ModConstants.ICON_KEYBIND,
                        backgroundX,                keycapY, 4,             16,  0, 0,  4, 16, 16, 16);
                context.drawTexture(ModConstants.ICON_KEYBIND,
                        backgroundX + 4,            keycapY, faceWidth - 8, 16,  4, 0,  8, 16, 16, 16);
                context.drawTexture(ModConstants.ICON_KEYBIND,
                        backgroundX + faceWidth - 4, keycapY, 4,            16, 12, 0,  4, 16, 16, 16);

                // Label centred inside the face rect
                int labelX = backgroundX + (faceWidth  - labelWidth) / 2;
                int labelY = backgroundY + (faceHeight - fontHeight)  / 2;
                context.drawText(textRenderer, label, labelX, labelY, ModConstants.KEYBIND_LABEL_COLOR, false);
            }

            i = j;
        }
    }

    /**
     * Returns the special run type for a given {@link Style} by inspecting the sentinel
     * font {@link Identifier} embedded by {@code HtmlConverter}:
     * <ul>
     *   <li>{@code 1} – chatcommand ({@link ModConstants#RUN_FONT_CHATCOMMAND})</li>
     *   <li>{@code 2} – keybind    ({@link ModConstants#RUN_FONT_KEYBIND})</li>
     *   <li>{@code 0} – normal text (any other font, including the default)</li>
     * </ul>
     */
    private int detectSpecialRunType(Style style) {
        Identifier font = style.getFont();
        if (ModConstants.RUN_FONT_CHATCOMMAND.equals(font)) return 1;
        if (ModConstants.RUN_FONT_KEYBIND.equals(font))     return 2;
        return 0;
    }

    /**
     * Returns the {@link Style} of the character under the mouse cursor for the given
     * rendered line, or {@code previous} if the mouse is not on this line.
     */
    private @Nullable Style pickHoverStyle(int mouseX, int mouseY,
                                            int fontHeight,
                                            OrderedText line,
                                            int drawY, int startX,
                                            @Nullable Style previous) {

        if (mouseY >= drawY && mouseY < drawY + fontHeight && mouseX >= startX) {
            Style s = textRenderer.getTextHandler().getStyleAt(line, mouseX - startX);
            if (s != null) return s;
        }
        return previous;
    }

    /**
     * Value returned by {@link #render}.
     */
    public static class RenderResult {

        /** Total pixel height of all rendered content blocks (used to compute the scrollbar maximum). */
        public int totalHeight;

        /** The {@link Style} under the mouse cursor, or {@code null} if the mouse is not over any styled text. */
        public Style hoveredStyle;

        public RenderResult() {
            totalHeight  = 0;
            hoveredStyle = null;
        }
    }
}

