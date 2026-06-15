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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GuideScreenLink class
 */
class GuideScreenLinkTest {

    /**
     * If this guard is missing, config.lastPage stores a garbled link that opens the wrong screen on the user's next launch.
     */
    @Test
    void encodePage_nullPage_returnsGuide() {

        assertEquals(GuideScreenLink.GUIDE, GuideScreenLink.encodePage(null, makeEntryUnchecked("e")));
    }

    /**
     * Helper to create a new entry - avoids noisy try-catches in tests
     *
     * @param id the entry id
     * @return the created entry
     */
    private static GuideEntry makeEntryUnchecked(String id) {
        try {
            return makeEntry(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reflective helper to create a guide entry
     *
     * @param id the entry's id
     * @return the created entry
     * @throws Exception when a reflection error occurs
     */
    private static GuideEntry makeEntry(String id) throws Exception {

        GuideEntry entry = new GuideEntry();

        Field field = GuideEntry.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entry, id);

        return entry;
    }

    /**
     * Validates that a null entry returns the landing page
     */
    @Test
    void encodePage_nullEntry_returnsGuide() {
        assertEquals(GuideScreenLink.GUIDE, GuideScreenLink.encodePage(makePageUnchecked("p"), null));
    }

    /**
     * Helper to create a new page - avoids noisy try-catches in tests
     *
     * @param id the page ID
     * @return the created guide page
     */
    private static GuidePage makePageUnchecked(String id) {
        try {
            return makePage(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reflective helper to create a guide page
     *
     * @param id      the page id
     * @param entries the associated list of entries
     * @return the created page
     * @throws Exception when a reflection error occurs
     */
    private static GuidePage makePage(String id, GuideEntry... entries) throws Exception {

        GuidePage page = new GuidePage();

        Field idField = GuidePage.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(page, id);

        Field entriesField = GuidePage.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        entriesField.set(page, Arrays.asList(entries));

        return page;
    }

    /**
     * A blank ID looks valid but resolve() will never find it; falling back to landing prevents the user from being
     * stuck on an unresolvable deep-link.
     */
    @Test
    void encodePage_blankPageId_returnsGuide() {

        GuidePage page = makePageUnchecked("");
        GuideEntry entry = makeEntryUnchecked("entry");

        assertEquals(GuideScreenLink.GUIDE, GuideScreenLink.encodePage(page, entry));
    }

    /**
     * If the format changes here without a matching change in resolve(), deep-links silently stop working for all users
     * who had a guide page saved.
     */
    @Test
    void encodePage_validPageAndEntry_returnsFormattedLink() {

        GuidePage page = makePageUnchecked("start_here");
        GuideEntry entry = makeEntryUnchecked("about");
        assertEquals("guide:page:start_here/about", GuideScreenLink.encodePage(page, entry));
    }

    /**
     * Null must not throw; the caller falls back to the landing page.
     */
    @Test
    void resolve_nullLink_returnsNull() {

        assertNull(GuideScreenLink.resolve(null, null));
    }

    /**
     * "guide" is the landing page token, not a page link; it must not falsely match.
     */
    @Test
    void resolve_bareGuideToken_returnsNull() {

        assertNull(GuideScreenLink.resolve("guide", null));
    }

    /**
     * "guide:map" is handled by the caller; this method must not hijack it.
     */
    @Test
    void resolve_mapLink_returnsNull() {

        assertNull(GuideScreenLink.resolve(GuideScreenLink.GUIDE_MAP, null));
    }

    @Test
    void resolve_missingSlash_returnsNull() {
        // A malformed link (no '/' separator) must not throw - it must fall back silently.
        assertNull(GuideScreenLink.resolve("guide:page:noslash", null));
    }

    /**
     * The core use case: wrong indices would display the wrong guide entry to the user.
     *
     * @throws Exception when a reflection error occurs
     */
    @Test
    void resolve_validLink_returnsCorrectPageAndEntryIndices() throws Exception {

        GuideBook book = makeBook(
                makePage("intro", makeEntry("welcome"), makeEntry("overview")),
                makePage("explore", makeEntry("maps"), makeEntry("regions"))
        );

        GuideScreenLink.Resolved resolved = GuideScreenLink.resolve("guide:page:explore/regions", book);

        assertNotNull(resolved);
        assertEquals(1, resolved.pageIndex(), "explore is the second page (index 1)");
        assertEquals(1, resolved.entryIndex(), "regions is the second entry (index 1)");
    }

    /**
     * Reflective helper to create a book
     *
     * @param pages the array of pages composing the book
     * @return the created book
     * @throws Exception when a reflection error occurs
     */
    private static GuideBook makeBook(GuidePage... pages) throws Exception {
        GuideBook book = new GuideBook();
        Field field = GuideBook.class.getDeclaredField("pages");
        field.setAccessible(true);
        field.set(book, Arrays.asList(pages));
        return book;
    }

    /**
     * A page removed from the guide book must not crash when an old saved link references it.
     *
     * @throws Exception when a reflection error occurs
     */
    @Test
    void resolve_unknownPageId_returnsNull() throws Exception {

        GuideBook book = makeBook(makePage("intro", makeEntry("welcome")));
        assertNull(GuideScreenLink.resolve("guide:page:gone/entry", book));
    }

    /**
     * This flag routes the user to MapScreen; a false negative opens the wrong screen.
     */
    @Test
    void isMapLink_exactToken_returnsTrue() {

        assertTrue(GuideScreenLink.isMapLink(GuideScreenLink.GUIDE_MAP));
        assertFalse(GuideScreenLink.isMapLink("guide"));
    }

    /**
     * This flag routes the user to Configuration; a false negative opens the wrong screen.
     */
    @Test
    void isConfigLink_exactToken_returnsTrue() {

        assertTrue(GuideScreenLink.isConfigLink(GuideScreenLink.GUIDE_CONFIG));
        assertFalse(GuideScreenLink.isConfigLink("guide:map"));
    }
}
