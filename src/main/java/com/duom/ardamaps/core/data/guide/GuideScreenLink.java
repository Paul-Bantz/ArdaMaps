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

import org.jetbrains.annotations.Nullable;

/**
 * Utility for encoding and resolving ArdaMaps GUI deep-links.
 *
 * <p>Link formats:</p>
 * <ul>
 *   <li>{@code "guide"} - Guide landing page</li>
 *   <li>{@code "guide:map"} - Map screen</li>
 *   <li>{@code "guide:configuration"} - Configuration screen</li>
 *   <li>{@code "guide:page:<pageId>/<entryId>"} - Guide at a specific page and entry</li>
 * </ul>
 *
 * <p>Any link that cannot be resolved falls back silently to {@code "guide"}.</p>
 */
public final class GuideScreenLink {

    public static final String GUIDE         = "guide";
    public static final String GUIDE_MAP     = "guide:map";
    public static final String GUIDE_CONFIG  = "guide:configuration";

    private static final String GUIDE_PAGE_PREFIX = "guide:page:";

    private GuideScreenLink() {}

    /**
     * Encodes a guide-page deep-link from a page and an entry.
     *
     * <p>Returns {@code "guide"} if either {@code page} or {@code entry} has a
     * {@code null} {@code id}, since the link would be unresolvable later.</p>
     *
     * @param page  the currently selected {@link GuidePage}
     * @param entry the currently selected {@link GuideEntry}
     * @return a deep-link string of the form {@code "guide:page:<pageId>/<entryId>"},
     *         or {@code "guide"} if either ID is absent
     */
    public static String encodePage(GuidePage page, GuideEntry entry) {
        if (page == null || entry == null) return GUIDE;
        String pageId  = page.getId();
        String entryId = entry.getId();
        if (pageId == null || pageId.isBlank() || entryId == null || entryId.isBlank()) return GUIDE;
        return GUIDE_PAGE_PREFIX + pageId + "/" + entryId;
    }

    /**
     * Result of a successful deep-link resolution.
     *
     * @param pageIndex  zero-based index of the matching page in {@link GuideBook#getPages()}
     * @param entryIndex zero-based index of the matching entry within that page
     */
    public record Resolved(int pageIndex, int entryIndex) {}

    /**
     * Resolves a deep-link string against a loaded {@link GuideBook}.
     *
     * <p>Returns {@code null} for any of the following cases (caller should fall back
     * to the landing page):</p>
     * <ul>
     *   <li>the link is {@code null}, blank, or equals {@code "guide"}</li>
     *   <li>the link starts with {@code "guide:page:"} but the IDs cannot be found</li>
     *   <li>{@code guideBook} is {@code null}</li>
     * </ul>
     *
     * <p>Links of {@code "guide:map"} and {@code "guide:configuration"} do not refer to
     * guide sub-pages; this method returns {@code null} for them too, and callers in
     * {@link com.duom.ardamaps.ArdaMapsClient} handle them separately.</p>
     *
     * @param link      the raw link string stored in {@code ClientConfig#lastPage}
     * @param guideBook the fully loaded guide book to search
     * @return a {@link Resolved} pair, or {@code null} if the link cannot be resolved to a page+entry
     */
    @Nullable
    public static Resolved resolve(@Nullable String link, @Nullable GuideBook guideBook) {
        if (link == null || link.isBlank() || GUIDE.equals(link)) return null;
        if (!link.startsWith(GUIDE_PAGE_PREFIX)) return null;
        if (guideBook == null) return null;

        String path = link.substring(GUIDE_PAGE_PREFIX.length()); // "<pageId>/<entryId>"
        int slash = path.indexOf('/');
        if (slash < 0) return null;

        String pageId = path.substring(0, slash);
        String entryId = path.substring(slash + 1);
        if (pageId.isBlank() || entryId.isBlank()) return null;

        var pages = guideBook.getPages();
        for (int pi = 0; pi < pages.size(); pi++) {
            GuidePage page = pages.get(pi);
            if (!pageId.equals(page.getId())) continue;
            var entries = page.getEntries();
            for (int ei = 0; ei < entries.size(); ei++) {
                if (entryId.equals(entries.get(ei).getId())) {
                    return new Resolved(pi, ei);
                }
            }
        }

        return null; // IDs not found - fall back to landing
    }

    /** @return {@code true} if the link should open the Map screen */
    public static boolean isMapLink(@Nullable String link) {
        return GUIDE_MAP.equals(link);
    }

    /** @return {@code true} if the link should open the Configuration screen */
    public static boolean isConfigLink(@Nullable String link) {
        return GUIDE_CONFIG.equals(link);
    }
}

