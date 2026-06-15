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

package com.duom.ardamaps.core.data.conversion;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.TextContentBlockRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.*;
import net.minecraft.util.math.ColorHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Singleton-Utility class for converting HTML strings into Minecraft {@link MutableText} objects.
 *
 * <p>This is a simplified HTML parser that supports a curated subset of tags:
 * <ul>
 *   <li>{@code <a>} – internal and external hyperlinks</li>
 *   <li>{@code <em>} – emphasized (medium-blue) text</li>
 *   <li>{@code <strong>} – bold medium-blue text</li>
 *   <li>{@code <blockquote>} – grey, italic quotation text</li>
 *   <li>{@code <br>} – explicit line breaks</li>
 *   <li>{@code <ul>} / {@code <li>} – unordered lists</li>
 *   <li>{@code <img>} – inline images split into {@link ContentBlock.ImageBlock}s</li>
 *   <li>{@code <chatcommand>} – chat command styling (orange, bold)</li>
 *   <li>{@code <keybind>} – resolved keybind labels</li>
 *   <li>{@code <username>} – substituted with the current player's name</li>
 * </ul>
 *
 * <p>All public entry points are static; the class cannot be instantiated.
 */
public class HtmlConverter {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlConverter.class);

    /** A regex pattern to parse href attributes in the format of "/type/id". */
    private static final Pattern HREF_PATTERN = Pattern.compile("^/([^/]+)/([^/]+)$");

    private HtmlConverter() {/* Class should not be instantiated */}

    /**
     * Parses an HTML string into a list of {@link ContentBlock}s, splitting the content
     * into {@link ContentBlock.TextBlock} segments and {@link ContentBlock.ImageBlock}s
     * wherever an {@code <img>} element is encountered.
     *
     * <p>{@code <img>} tags must carry numeric {@code width} and {@code height} attributes
     * and an HTTP {@code src} URL.  If either dimension is missing or non-numeric a warning
     * is logged and the image is skipped entirely so the layout remains stable.</p>
     *
     * @param html raw HTML string
     * @return ordered list of content blocks ready for rendering
     */
    public static List<ContentBlock> parseBlocks(String html) {

        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        List<ContentBlock> blocks = new ArrayList<>();
        ParseBuffer buf = new ParseBuffer();

        for (Node node : doc.body().childNodes())
            collectNodes(node, blocks, buf, TextColor.fromRgb(ModConstants.COLOR_DARKER_BLUE));

        buf.flushTo(blocks);
        return blocks;
    }

    /**
     * Recursively walks {@code node}, appending styled text to {@code buf} and emitting
     * {@link ContentBlock.ImageBlock}s (after flushing the buffer) whenever an
     * {@code <img>} element is encountered.
     *
     * <p>Unknown or generic block-level elements (e.g. {@code <p>}, {@code <div>},
     * {@code <span>}) are handled by recursing into their children with the current colour
     * unchanged.</p>
     *
     * @param node   the HTML node to process
     * @param blocks accumulator list that receives completed {@link ContentBlock}s
     * @param buf    the mutable text buffer currently being built
     * @param color  the {@link TextColor} to apply to plain text content
     */
    private static void collectNodes(Node node,
                                     List<ContentBlock> blocks,
                                     ParseBuffer buf,
                                     TextColor color) {

        collectNodes(node, blocks, buf, color, false, false);
    }

    /**
     * Recursively walks {@code node}, appending styled text to {@code buf} and emitting
     * {@link ContentBlock.ImageBlock}s (after flushing the buffer) whenever an
     * {@code <img>} element is encountered.
     *
     * <p>Unknown or generic block-level elements (e.g. {@code <p>}, {@code <div>},
     * {@code <span>}) are handled by recursing into their children with the current colour
     * unchanged.</p>
     *
     * @param node   the HTML node to process
     * @param blocks accumulator list that receives completed {@link ContentBlock}s
     * @param buf    the mutable text buffer currently being built
     * @param color  the {@link TextColor} to apply to plain text content
     * @param stripLeading   whether to strip leading whitespace from text nodes (typically enabled for block-level elements)
     *                       @param stripTrailing   whether to strip trailing whitespace from text nodes (typically enabled for block-level elements)
     */
    private static void collectNodes(Node node,
                                     List<ContentBlock> blocks,
                                     ParseBuffer buf,
                                     TextColor color,
                                     boolean stripLeading,
                                     boolean stripTrailing) {

        if (node instanceof TextNode textNode) {

            if (!textNode.isBlank()) {

                var rawText = textNode.text();

                if (stripLeading || rawText.startsWith("\n")) rawText = rawText.stripLeading();
                if (stripTrailing) rawText = rawText.stripTrailing();

                buf.append(Text.literal(rawText).styled(s -> s.withColor(color)));
            }

            return;
        }

        if (!(node instanceof Element element)) return;

        switch (element.tagName()) {

            case "img" -> collectImgNode(blocks, buf, element);
            case "blockquote" -> collectBlockQuoteNode(blocks, buf, element);
            case "em" -> collectEmNode(blocks, buf, element);
            case "strong" -> collectStrongNode(element, buf, blocks);
            case "chatcommand" -> collectChatCommandNode(element, buf, blocks);
            case "keybind" -> collectKeybindNode(buf, element);
            case "username" -> collectUsernameNode(buf);
            case "ul" -> collectUlNode(blocks, buf, color, element);
            case "a" -> collectLinkNode(buf, element);
            case "h1", "h2", "h3", "h4", "h5", "h6" -> collectTitleNode(blocks, buf, element);
            case "br" -> {
                buf.flushTo(blocks);
                blocks.add(new ContentBlock.LineBreakBlock());
            }
            case "p" -> {
                buf.flushTo(blocks);
                blocks.add(new ContentBlock.SeparatorBlock());
                collectTextNode(blocks, buf, color, element, true);
                buf.flushTo(blocks);
                blocks.add(new ContentBlock.SeparatorBlock());
            }
            // p, div, span, etc. – recurse with same colour
            default -> collectTextNode(blocks, buf, color, element);
        }
    }

    /**
     * Recursively processes the children of a generic block-level or inline {@link Element}
     * (e.g. {@code <p>}, {@code <div>}, {@code <span>}) and appends their content to
     * {@code buf} using the supplied {@code color}.
     *
     * @param blocks  accumulator list passed through to {@link #collectNodes}
     * @param buf     the mutable text buffer currently being built
     * @param color   the {@link TextColor} to propagate to child nodes
     * @param element the element whose children should be walked
     */
    private static void collectTextNode(List<ContentBlock> blocks,
                                        ParseBuffer buf,
                                        TextColor color,
                                        Element element) {
        for (Node child : element.childNodes()) {
            collectNodes(child, blocks, buf, color, false, false);
        }
    }

    /**
     * Recursively processes the children of a generic block-level or inline {@link Element}
     * (e.g. {@code <p>}, {@code <div>}, {@code <span>}) and appends their content to
     * {@code buf} using the supplied {@code color}.
     *
     * @param blocks  accumulator list passed through to {@link #collectNodes}
     * @param buf     the mutable text buffer currently being built
     * @param color   the {@link TextColor} to propagate to child nodes
     * @param element the element whose children should be walked
     * @param trim  whether to trim leading/trailing whitespace from text nodes (typically true for block-level elements)
     */
    @SuppressWarnings("SameParameterValue")
    private static void collectTextNode(List<ContentBlock> blocks,
                                        ParseBuffer buf,
                                        TextColor color,
                                        Element element,
                                        boolean trim) {

        for (int idx = 0; idx < element.childNodeSize(); idx++) {

            var stripLeading = trim && (idx == 0);
            var stripTrailing = trim && (idx == element.childNodeSize() - 1);

            if (!buf.hasContent && !blocks.isEmpty()) {

                var previousBlock = blocks.get(blocks.size()-1);

                // If we are following a line break, strip leading whitespaces
                if (previousBlock instanceof ContentBlock.LineBreakBlock) {
                    stripLeading = true;
                }
            }

            collectNodes(element.childNode(idx),
                    blocks,
                    buf,
                    color,
                    stripLeading,
                    stripTrailing);
        }
    }

    /**
     * Collects a title type node
     * @param blocks the current blocks list, used to flush the buffer before and after the title block
     * @param buf the current buffer
     * @param element the element to collect
     */
    private static void collectTitleNode(List<ContentBlock> blocks, ParseBuffer buf, Element element) {

        buf.flushTo(blocks);
        blocks.add(new ContentBlock.SeparatorBlock());

        StringBuilder builder = new StringBuilder();

        for (Node child : element.childNodes()) {

            if (child instanceof TextNode textNode) {

                if (!textNode.isBlank())
                    builder.append(textNode.text());

            }
        }

        blocks.add(new ContentBlock.TitleBlock(builder.toString(), ContentBlock.TitleBlock.Type.fromString(element.tagName())));
        blocks.add(new ContentBlock.SeparatorBlock());
    }

    /**
     * Processes an {@code <a>} element and appends the resulting styled link text to
     * {@code buf}.
     *
     * <p>Delegates to {@link #processExternalLinkType} when the element carries
     * {@code type="external"}, and to {@link #processInternalLinkType} otherwise.</p>
     *
     * @param buf     the mutable text buffer to append the link fragment to
     * @param element the {@code <a>} element to process
     */
    private static void collectLinkNode(ParseBuffer buf, Element element) {
        var linkType = element.attr("type");
        MutableText linkText = linkType.equals("external")
                ? processExternalLinkType(element)
                : processInternalLinkType(element);
        if (linkText != null) buf.append(linkText);
    }

    /**
     * Processes a {@code <ul>} element by collecting its {@code <li>} children into a
     * {@link ContentBlock.ListBlock} that is added directly to {@code blocks}.
     *
     * <p>The current buffer is flushed before the list block is emitted so that
     * preceding inline text ends up in its own {@link ContentBlock.TextBlock}.</p>
     *
     * @param blocks  accumulator list that receives the finished {@link ContentBlock.ListBlock}
     * @param buf     the mutable text buffer; flushed before the list is added
     * @param color   the {@link TextColor} to apply to list-item text
     * @param element the {@code <ul>} element to process
     */
    private static void collectUlNode(List<ContentBlock> blocks, ParseBuffer buf, TextColor color, Element element) {

        // Collect <li> children into a ListBlock
        buf.flushTo(blocks);
        List<MutableText> items = new ArrayList<>();

        for (Node child : element.childNodes()) {

            if (child instanceof Element li && li.tagName().equals("li")) {

                MutableText itemText = Text.empty();

                for (Node liChild : li.childNodes()) {

                    ParseBuffer itemBuf = new ParseBuffer();
                    List<ContentBlock> ignored = new ArrayList<>();

                    collectNodes(liChild, ignored, itemBuf, color);
                    itemBuf.flushTo(ignored);

                    for (ContentBlock b : ignored) {
                        if (b instanceof ContentBlock.TextBlock tb) itemText.append(tb.text());
                    }
                }
                items.add(itemText);
            }
        }
        if (!items.isEmpty()) blocks.add(new ContentBlock.ListBlock(items));
    }

    /**
     * Processes a {@code <username>} element by resolving the currently authenticated
     * player's username from {@link MinecraftClient#getSession()} and appending it to
     * {@code buf} in medium-blue.
     *
     * @param buf the mutable text buffer to append the username fragment to
     */
    private static void collectUsernameNode(ParseBuffer buf) {
        // Self-closing: resolve the current player's name in medium-blue
        String username = MinecraftClient.getInstance().getSession().getUsername();
        buf.append(Text.literal(username)
                .styled(s -> s.withColor(TextColor.fromRgb(ModConstants.COLOR_BLUE))));
    }

    /**
     * Processes a {@code <keybind>} element and appends a fixed-width placeholder text to
     * {@code buf}.  Two forms are supported:
     * <ul>
     *   <li><b>Self-closing with {@code type}:</b> {@code <keybind type="key.ardamaps.open_map"/>}
     *       – resolves the currently bound key name via {@link #resolveKeyLabel}.</li>
     *   <li><b>Element with content:</b> {@code <keybind>CTRL</keybind>}
     *       – uses the element's text directly as a literal label.</li>
     * </ul>
     *
     * @param buf     the mutable text buffer to append the keybind placeholder to
     * @param element the {@code <keybind>} element to process
     */
    private static void collectKeybindNode(ParseBuffer buf, Element element) {
        String keyId = element.attr("type");
        String label;
        if (!keyId.isBlank()) {
            label = resolveKeyLabel(keyId);
        } else {
            label = element.text().strip();
            if (label.isBlank()) {
                LOGGER.warn("[HtmlConverter] <keybind> has neither a 'type' attribute nor text content – skipping.");
                return;
            }
        }

        var text = buildKeybindText(label);
        text.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("ardamaps.client.generic.keybind_options"))));

        buf.append(text);
    }

    /**
     * Processes a {@code <strong>} element, rendering its text children in medium-blue and
     * appending the result to {@code buf}.
     *
     * <p>Non-text child nodes are forwarded to {@link #collectNodes} so that nested markup
     * (e.g. a link inside a {@code <strong>}) is still handled correctly.</p>
     *
     * @param element the element to process
     * @param buf     the mutable text buffer to append styled fragments to
     * @param blocks  accumulator list passed through to {@link #collectNodes} for nested nodes
     */
    private static void collectStrongNode(Element element, ParseBuffer buf, List<ContentBlock> blocks) {
        TextColor textColor = TextColor.fromRgb(ModConstants.COLOR_BLUE);

        for (Node child : element.childNodes()) {

            if (child instanceof TextNode textNode && !textNode.isBlank()) {

                var text = textNode.text();
                buf.append(Text.literal(text).styled(style -> style.withColor(textColor)));

            } else {

                collectNodes(child, blocks, buf, textColor);
            }
        }
    }

    /**
     * Processes a {@code <chatcommand>} element, rendering its text children in the
     * command colour ({@link ModConstants#TEXT_COLOR_COMMAND}) with the
     * {@link ModConstants#RUN_FONT_CHATCOMMAND} sentinel font and an insertion string so
     * that {@link com.duom.ardamaps.gui.screens.GuideScreen} can pre-populate the chat box on Shift+click.
     *
     * <p>The insertion equals the raw text of each child {@link TextNode} so that
     * GuideScreen#mouseClicked can read it back via {@link Style#getInsertion()}.
     * {@code Style} is immutable; each {@code withXxx} call returns a new instance and the
     * result must be captured – hence {@code style = style.withInsertion(text)}.</p>
     *
     * <p>Non-text child nodes are forwarded to {@link #collectNodes} so that nested markup
     * inside a chatcommand is still handled correctly.</p>
     *
     * @param element the element to process
     * @param buf     the mutable text buffer to append styled fragments to
     * @param blocks  accumulator list passed through to {@link #collectNodes} for nested nodes
     */
    private static void collectChatCommandNode(Element element, ParseBuffer buf, List<ContentBlock> blocks) {
        TextColor textColor = TextColor.fromRgb(ModConstants.TEXT_COLOR_COMMAND);

        for (Node child : element.childNodes()) {

            if (child instanceof TextNode textNode && !textNode.isBlank()) {

                var text = textNode.text();
                buf.append(Text.literal(text)
                        .styled(style -> {
                            style = style.withColor(textColor);
                            style = style.withFont(ModConstants.RUN_FONT_CHATCOMMAND);
                            style = style.withInsertion(text);
                            style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("ardamaps.client.generic.run_command")));
                            return style;
                        }));

            } else {

                collectNodes(child, blocks, buf, textColor);
            }
        }
    }

    /**
     * Processes an {@code <em>} element by delegating to {@link #collectTextNode} with
     * {@link ModConstants#COLOR_BLUE}, giving the emphasized text its
     * distinctive medium-blue colour.
     *
     * @param blocks  accumulator list passed through for any nested block-level elements
     * @param buf     the mutable text buffer to append the emphasized text to
     * @param element the {@code <em>} element to process
     */
    private static void collectEmNode(List<ContentBlock> blocks, ParseBuffer buf, Element element) {
        collectTextNode(blocks, buf, TextColor.fromRgb(ModConstants.COLOR_BLUE), element);
    }

    /**
     * Processes a {@code <blockquote>} element by flushing the current buffer, collecting the
     * blockquote's children into a fresh {@link ParseBuffer} using
     * {@link ModConstants#COLOR_BLUE}, and emitting a
     * {@link ContentBlock.BlockquoteBlock} directly into {@code blocks}.
     *
     * <p>The block is self-contained: the renderer adds {@code ⌈fontHeight/2⌉} px of vertical
     * margin above and below, and draws a left accent bar in the same medium-blue colour.
     * No explicit trailing newline is needed here – the outer loop's
     * {@code appendNewlineIfHasContent()} call handles separation from subsequent content.</p>
     *
     * @param blocks  accumulator list that receives the finished {@link ContentBlock.BlockquoteBlock}
     * @param buf     the mutable text buffer; flushed before the blockquote block is added
     * @param element the {@code <blockquote>} element to process
     */
    private static void collectBlockQuoteNode(List<ContentBlock> blocks, ParseBuffer buf, Element element) {
        buf.flushTo(blocks);

        TextColor bqColor = TextColor.fromRgb(ModConstants.COLOR_BLUE);
        ParseBuffer bqBuf  = new ParseBuffer();
        List<ContentBlock> bqBlocks = new ArrayList<>();

        for (Node child : element.childNodes()) {
            collectNodes(child, bqBlocks, bqBuf, bqColor);
        }
        bqBuf.flushTo(bqBlocks);

        for (ContentBlock b : bqBlocks) {
            if (b instanceof ContentBlock.TextBlock tb) {
                blocks.add(new ContentBlock.BlockquoteBlock(tb.text()));
            } else if (b instanceof ContentBlock.LineBreakBlock) {
                blocks.add(new ContentBlock.BlockquoteBlock(Text.literal("\n")));
            } else {
                // Images or other block-level content nested inside a blockquote are passed through as-is.
                blocks.add(b);
            }
        }
        blocks.add(new ContentBlock.SeparatorBlock());
    }

    /**
     * Flushes the current {@code buf} into {@code blocks} as a {@link ContentBlock.TextBlock},
     * then parses the {@code <img>} element and appends the resulting
     * {@link ContentBlock.ImageBlock} to {@code blocks} (or silently skips it if the element
     * is malformed).
     *
     * @param blocks  accumulator list that receives the new {@link ContentBlock.ImageBlock}
     * @param buf     the mutable text buffer; flushed before the image block is added
     * @param element the {@code <img>} element to process
     */
    private static void collectImgNode(List<ContentBlock> blocks, ParseBuffer buf, Element element) {
        buf.flushTo(blocks);
        ContentBlock.ImageBlock imgBlock = processImg(element);
        if (imgBlock != null) blocks.add(imgBlock);
    }

    /**
     * Parses an {@code <img>} element into an {@link ContentBlock.ImageBlock}.
     * Returns {@code null} (and logs a warning) if {@code src} is blank or if
     * {@code width} / {@code height} are missing or non-numeric.
     *
     * @param element the {@code <img>} element to parse
     * @return a fully initialized {@link ContentBlock.ImageBlock}, or {@code null} on error
     */
    private static ContentBlock.ImageBlock processImg(Element element) {

        String src = element.attr("src");
        String widthStr = element.attr("width");
        String heightStr = element.attr("height");
        String alignStr = element.attr("align");
        String caption = element.attr("caption");

        if (src.isBlank()) {
            LOGGER.warn("[HtmlConverter] <img> is missing a 'src' attribute – skipping.");
            return null;
        }

        Optional<Integer> width = parseInt(widthStr);
        Optional<Integer> height = parseInt(heightStr);

        if (width.isEmpty() || height.isEmpty()) return null;

        ContentBlock.ImageBlock.Align align = ContentBlock.ImageBlock.Align.fromString(alignStr);
        return new ContentBlock.ImageBlock(src, width.get(), height.get(), align, caption);
    }

    /**
     * Attempts to parse {@code text} as a base-10 integer.
     *
     * <p>Returns an empty {@link Optional} and logs a warning if {@code text} is blank,
     * {@code null}, or not a valid integer string.</p>
     *
     * @param text the string to parse
     * @return an {@link Optional} containing the parsed value, or empty on failure
     */
    private static Optional<Integer> parseInt(String text) {

        Optional<Integer> result;

        try {
            result = Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            LOGGER.warn("Unable to parse {} as an integer", text);
            return Optional.empty();
        }

        return result;
    }

    /**
     * Builds a styled {@link MutableText} for an external link ({@code type="external"}).
     *
     * <p>The resulting text is rendered in dark-blue and carries both a
     * {@link ClickEvent} that opens {@code href} in the system browser and a
     * {@link HoverEvent} that displays the URL as a tooltip.</p>
     *
     * @param element the {@code <a type="external">} element to process
     * @return a fully styled {@link MutableText} representing the external link
     */
    private static MutableText processExternalLinkType(Element element) {

        var href = element.attr("href");

        return Text.literal(element.text())
                .styled(style -> style
                        .withColor(ColorHelper.Argb.getArgb(255, 48, 79, 110))
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                href
                        ))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("ardamaps.client.generic.open_external_link", href))
                        )
                );
    }

    /**
     * Default links (no type) are internal references. They should redirect to a page that is referenced by the mod.
     *
     * <p>The link text is coloured dark-blue. If the referenced location has been
     * explored the tooltip reads "more information" and the click event carries the
     * location ID; otherwise the tooltip reads "unknown location" and the click event is
     * effectively a no-op (empty string).</p>
     *
     * @param element the html element to parse
     * @return the MutableText representing the link, or {@code null} if the {@code href}
     * could not be parsed
     */
    private static MutableText processInternalLinkType(Element element) {

        var link = parseLocationLinkTarget(element.attr("href"));

        if (link.isPresent()) {

            var value = link.get();
            var tooltip = Text.translatable("ardamaps.location.unknown");
            var linkValue = "";

            var location = ArdaMapsClient.CONFIG.getLocations()
                    .stream()
                    .filter(locationClient -> Objects.equals(locationClient.getId(), value.id()))
                    .findFirst();

            if (location.isPresent()) {

                var resolvedLocation = location.get();
                var locationPosition = resolvedLocation.getPosition();

                if (!ArdaMapsClient.CONFIG.isMapRevealAll()) {

                    var exploration = ArdaMapsClient.CONFIG.getClientProgress().getExplorationState().get(resolvedLocation.getWorld());

                    if (exploration != null && exploration.isWorldPosExplored(locationPosition.x, locationPosition.z, 0)) {

                        if (resolvedLocation.isVisited()) {

                            tooltip = Text.translatable("ardamaps.client.generic.more.information");
                            linkValue = value.id();
                        } else {

                            tooltip = Text.translatable("ardamaps.location.not_visited");
                        }
                    }

                } else {

                    tooltip = Text.translatable("ardamaps.client.generic.more.information");
                    linkValue = value.id();
                }
            }

            final var resolvedLink = linkValue;
            final var resolvedTooltip = tooltip;

            return Text.literal(element.text())
                    .styled(style -> style
                            .withColor(ColorHelper.Argb.getArgb(255, 48, 79, 110))
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.OPEN_URL,
                                    resolvedLink
                            ))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, resolvedTooltip))
                    );
        }

        return null;
    }

    /**
     * Parse the input string into its components (type and name).
     *
     * <p>The expected format is {@code "/type/id"}, e.g. {@code "/location/rivendell"}.
     * Returns an empty {@link Optional} when {@code href} is blank or does not match
     * {@link #HREF_PATTERN}.</p>
     *
     * @param href the string to parse
     * @return an {@link Optional} containing the resulting {@link Link}, or empty if the
     * input is invalid
     */
    private static Optional<Link> parseLocationLinkTarget(String href) {

        if (href == null || href.isBlank()) return Optional.empty();

        Matcher matcher = HREF_PATTERN.matcher(href.trim());

        if (!matcher.matches()) return Optional.empty();

        String type = matcher.group(1);
        String name = matcher.group(2);

        return Optional.of(new Link(type, name));
    }

    /**
     * Resolves a keybind translation key to the human-readable name of the currently
     * bound key.
     *
     * <p>Searches {@link net.minecraft.client.option.GameOptions#allKeys} for the
     * {@link KeyBinding} whose translation key matches {@code keyId}. Falls back to
     * {@code "?"} (with a warning) when no matching binding is found.</p>
     *
     * @param keyId the keybind translation key, e.g. {@code "key.ardamaps.open_map"}
     * @return the localized key name (e.g. {@code "M"}), or {@code "?"} on failure
     */
    private static String resolveKeyLabel(String keyId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            for (KeyBinding kb : mc.options.allKeys) {
                if (kb.getTranslationKey().equals(keyId)) {
                    return kb.getBoundKeyLocalizedText().getString();
                }
            }
        }
        LOGGER.warn("[HtmlConverter] <keybind type=\"{}\"> – no matching KeyBinding found, using fallback.", keyId);
        return "?";
    }

    /**
     * Builds a fixed-width placeholder {@link MutableText} for a keybind label.
     *
     * <p>The text consists of space characters sized so the run is at least
     * {@link ModConstants#MIN_KEYBIND_SLOT_PX} pixels wide and wide enough to contain
     * {@code label} without clipping.  The visible label is embedded in every
     * character's {@link Style} via {@link Style#withInsertion(String)}, which
     * {@link TextContentBlockRenderer} reads back
     * at draw time to render the label centered inside the key-cap rectangle.</p>
     *
     * <p><b>Note on {@code insertion} reuse:</b> {@code Style.insertion} normally inserts
     * text on Shift+click in chat.  Guide content is never processed by Minecraft's chat
     * input system, so that behaviour is never triggered here.</p>
     *
     * @param label the visible key name (e.g. {@code "M"}, {@code "CTRL"})
     * @return a space-filled {@link MutableText} carrying the label in its {@link Style}
     */
    private static MutableText buildKeybindText(String label) {
        MinecraftClient mc = MinecraftClient.getInstance();

        int labelWidth;
        int spaceWidth;
        if (mc != null && mc.textRenderer != null) {
            labelWidth = mc.textRenderer.getWidth(label);
            spaceWidth = mc.textRenderer.getWidth(" ");
        } else {
            labelWidth = label.length() * 6; // conservative fallback
            spaceWidth = 4;
        }
        if (spaceWidth <= 0) spaceWidth = 4;

        int slotWidth = Math.max(ModConstants.MIN_KEYBIND_SLOT_PX, labelWidth);
        int numSpaces = Math.max(2, (int) Math.ceil((double) slotWidth / spaceWidth));
        String placeholder = " ".repeat(numSpaces);

        return Text.literal(placeholder)
                .styled(s -> s
                        .withFont(ModConstants.RUN_FONT_KEYBIND)
                        .withInsertion(label));
    }

    /**
     * A simple record to hold the type and id extracted from a href attribute.
     *
     * @param type the path segment identifying the resource type (e.g. {@code "location"})
     * @param id   the path segment identifying the specific resource (e.g. {@code "rivendell"})
     */
    private record Link(String type, String id) {
    }

    /**
     * Mutable accumulator used while building a {@link ContentBlock.TextBlock} inside
     * {@link #parseBlocks(String)}.  Extracted into its own class to allow clean
     * flushing when an {@code <img>} is encountered mid-stream.
     */
    private static final class ParseBuffer {

        private MutableText text = Text.empty();

        private boolean hasContent = false;

        /** Appends a pre-styled fragment and marks the buffer as non-empty. */
        void append(MutableText fragment) {
            text.append(fragment);
            hasContent = true;
        }

        /**
         * If the buffer holds any content, wraps it in a {@link ContentBlock.TextBlock}
         * and adds it to {@code blocks}, then resets the buffer.
         *
         * @param blocks the accumulator list to flush the current text into
         */
        void flushTo(List<ContentBlock> blocks) {
            if (hasContent) {
                blocks.add(new ContentBlock.TextBlock(text));
                text = Text.empty();
                hasContent = false;
            }
        }
    }
}