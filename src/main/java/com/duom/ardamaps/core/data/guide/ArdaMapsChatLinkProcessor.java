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

package com.duom.ardamaps.core.data.guide;

import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans incoming chat {@link Text} objects for ArdaMaps links (waypoints of guide deep-link tokens) and
 * re-styles each matching token as a clickable, underlined, blue hyperlink.
 *
 * <h3>Supported token formats</h3>
 * <ul>
 *   <li>{@code guide:page:<pageId>/<entryId>} - opens the guide at a specific page and entry</li>
 *   <li>{@code guide:map} - opens the Map screen</li>
 *   <li>{@code guide:configuration} - opens the Configuration screen</li>
 * </ul>
 *
 * <p>Tokens must start with {@code waypoint:} or {@code guide:} (strict prefix - lone "guide" is ignored to
 * prevent false positives on common English words).  The pattern anchors to a
 * word boundary on the left so that substrings of longer identifiers are not matched.</p>
 *
 * <h3>Click behaviour</h3>
 * <p>Each styled token carries a {@link ClickEvent} of type {@code RUN_COMMAND} pointing
 * to {@code /ardamaps guide <token>} or {@code /ardamaps waypoint <token>}.  That client-side command is registered by
 * {@link com.duom.ardamaps.core.commands.ClientCommands} and opens the appropriate screen or sets
 * the corresponding waypoint.</p>
 */
public final class ArdaMapsChatLinkProcessor {

    /**
     * Regex that matches any guide deep-link token starting with the literal prefix
     * {@code guide:} followed by one or more non-whitespace characters.
     *
     * <p>The leading {@code \b} word boundary prevents matching in the middle of a
     * larger word (e.g. {@code "maguide:foo"} is not matched).</p>
     */
    private static final Pattern GUIDE_LINK_PATTERN = Pattern.compile("\\bguide:\\S+");

    private ArdaMapsChatLinkProcessor() { /* utility class */ }


    /**
     * Processes a received chat or game message and replaces every guide deep-link token
     * it contains with a styled, clickable {@link Text} run.
     *
     * @param message the incoming chat text, never {@code null}
     * @return the original {@code message} if no guide or waypoints links are found, otherwise a new
     *         {@link MutableText} containing plain and styled segments
     */
    public static Text process(Text message) {

        Text result = processGuideLink(message);

        if (Objects.equals(result, message)) {

            // If no guide links have been found, try to find waypoint links
            result = processWaypointLink(result);
        }

        return result;
    }

    /**
     * Processes a received chat or game message and replaces every waypoint token
     * it contains with a styled, clickable {@link Text} run.
     *
     * <p>If the message contains no tokens matching ":waypoint" the
     * original {@code message} object is returned unchanged, avoiding unnecessary
     * allocations.</p>
     *
     * <p>The surrounding (non-link) text fragments are preserved as plain literal segments;
     * the original formatting of those segments is intentionally kept minimal because
     * most server chat messages consist of flat literal text. If the original message
     * carries complex sibling trees, only the top-level {@link Text#getString()} pass-through
     * is used - the tree is not walked recursively.</p>
     *
     * @param message the incoming chat text, never {@code null}
     * @return the original {@code message} if no waypoint links are found, otherwise a new
     *         {@link MutableText} containing plain and styled segments
     */
    public static @NotNull Text processWaypointLink(@NotNull Text message) {

        MutableText result = Text.empty();
        String raw = message.getString();

        if (!raw.contains("waypoint:")) return message;

        var waypointIndex = raw.indexOf("waypoint:");
        var waypointJsonIndex = waypointIndex + "waypoint:".length();

        if (waypointJsonIndex >= raw.length()) return message;

        var stringBeginning = raw.substring(0, waypointIndex);
        var waypointJsonString = raw.substring(waypointJsonIndex);

        // Styled guide-link run
        Optional<Waypoint> waypoint = Waypoint.fromJson(waypointJsonString);

        if (waypoint.isPresent()) {

            MutableText link = Text.literal("waypoint")
                    .styled(style -> style
                            .withColor(ModConstants.COLOR_BLUE)
                            .withUnderline(true)
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal(waypoint.get().text())))
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/ardamaps waypoint " + waypointJsonString
                            ))
                    );
            result.append(stringBeginning);
            result.append(link);
        }

        return result;
    }

    /**
     * Processes a received chat or game message and replaces every guide deep-link token
     * it contains with a styled, clickable {@link Text} run.
     *
     * <p>If the message contains no tokens matching {@link #GUIDE_LINK_PATTERN} the
     * original {@code message} object is returned unchanged, avoiding unnecessary
     * allocations.</p>
     *
     * <p>The surrounding (non-link) text fragments are preserved as plain literal segments;
     * the original formatting of those segments is intentionally kept minimal because
     * most server chat messages consist of flat literal text. If the original message
     * carries complex sibling trees, only the top-level {@link Text#getString()} pass-through
     * is used - the tree is not walked recursively.</p>
     *
     * @param message the incoming chat text, never {@code null}
     * @return the original {@code message} if no guide links are found, otherwise a new
     *         {@link MutableText} containing plain and styled segments
     */
    public static @NotNull Text processGuideLink(@NotNull Text message) {

        MutableText result = Text.empty();
        String raw = message.getString();
        Matcher guideMatcher = GUIDE_LINK_PATTERN.matcher(raw);

        // Fast path – nothing to transform
        if (!guideMatcher.find()) return message;

        int lastEnd = 0;
        guideMatcher.reset();

        while (guideMatcher.find()) {

            // Plain text before this match
            if (guideMatcher.start() > lastEnd) {
                result.append(Text.literal(raw.substring(lastEnd, guideMatcher.start())));
            }

            // Styled guide-link run
            String token = guideMatcher.group();
            MutableText link = Text.literal(token)
                    .styled(style -> style
                            .withColor(ModConstants.COLOR_BLUE)
                            .withUnderline(true)
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.translatable("ardamaps.client.chat.guide_link")
                            ))
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/ardamaps guide " + token
                            ))
                    );
            result.append(link);
            lastEnd = guideMatcher.end();
        }

        // Trailing plain text
        if (lastEnd < raw.length()) {
            result.append(Text.literal(raw.substring(lastEnd)));
        }

        return result;
    }
}

