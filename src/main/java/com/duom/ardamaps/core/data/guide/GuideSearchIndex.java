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

import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Session-scoped guide search index used by {@link com.duom.ardamaps.gui.screens.GuideScreen}.
 *
 * <p>The index is built asynchronously from guide entry HTML and reused across screen
 * re-initializations. Matching is title-first, then page-title, then content.</p>
 */
public final class GuideSearchIndex {

    /** Maximum number of concurrent HTML loads used to preload the guide search index. */
    private static final int SEARCH_PRELOAD_BATCH_SIZE = 8;

    /** Number of chars kept on each side of the first match in search snippets. */
    private static final int SEARCH_SNIPPET_RADIUS = 64;

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /** Signature of the currently cached search index (locale + page/entry identity). */
    private static volatile String searchIndexSignature = "";

    /** Signature currently being built asynchronously. */
    private static volatile String loadingSearchIndexSignature = "";

    /** Cached searchable guide entries reused between GuideScreen init calls. */
    private static volatile List<GuideSearchEntry> searchIndex = List.of();

    /** Whether an async index preload is currently running. */
    private static volatile boolean loadingSearchIndex;

    private GuideSearchIndex() {
    }

    private record GuideSearchEntry(int pageIndex,
                                    int entryIndex,
                                    String pageTitle,
                                    String entryTitle,
                                    String pageTitleSearch,
                                    String entryTitleSearch,
                                    String plainContent,
                                    String plainContentSearch) {
    }

    private record SearchSeed(int pageIndex,
                              int entryIndex,
                              String pageTitle,
                              String entryTitle,
                              String link,
                              String pageTitleSearch,
                              String entryTitleSearch) {
    }

    /**
     * Public result payload consumed by the guide search UI.
     *
     * @param pageIndex   zero-based page index
     * @param entryIndex  zero-based entry index
     * @param entryTitle  entry label shown first in the result
     * @param snippet     fixed-radius context snippet around the first match
     * @param matchRank   rank bucket (lower is better)
     * @param matchOffset first match position within the matched source
     */
    public record GuideSearchResult(int pageIndex,
                                    int entryIndex,
                                    String pageTitle,
                                    String entryTitle,
                                    String snippet,
                                    int matchRank,
                                    int matchOffset) {
    }

    /**
     * Starts async preload if the cached index is stale for the given book signature.
     */
    public static void preloadIfNeeded(@Nullable GuideBook book) {
        if (book == null) return;

        String signature = buildSearchIndexSignature(book);

        synchronized (GuideSearchIndex.class) {
            if (signature.equals(searchIndexSignature) && !searchIndex.isEmpty()) return;
            if (loadingSearchIndex && signature.equals(loadingSearchIndexSignature)) return;

            loadingSearchIndex = true;
            loadingSearchIndexSignature = signature;
        }

        var seeds = collectSearchSeeds(book);
        if (seeds.isEmpty()) {
            synchronized (GuideSearchIndex.class) {
                searchIndex = List.of();
                searchIndexSignature = signature;
                loadingSearchIndex = false;
                loadingSearchIndexSignature = "";
            }
            return;
        }

        List<GuideSearchEntry> built = java.util.Collections.synchronizedList(new ArrayList<>(seeds.size()));
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (int i = 0; i < seeds.size(); i += SEARCH_PRELOAD_BATCH_SIZE) {
            int from = i;
            int to = Math.min(i + SEARCH_PRELOAD_BATCH_SIZE, seeds.size());

            chain = chain.thenCompose(v -> {
                List<SearchSeed> batch = seeds.subList(from, to);

                CompletableFuture<?>[] futures = batch.stream()
                        .map(seed -> GuideLoader.loadHtml(seed.link()).thenAccept(html -> {
                            String plain = htmlToSearchablePlaintext(html);
                            built.add(new GuideSearchEntry(
                                    seed.pageIndex(),
                                    seed.entryIndex(),
                                    seed.pageTitle(),
                                    seed.entryTitle(),
                                    seed.pageTitleSearch(),
                                    seed.entryTitleSearch(),
                                    plain,
                                    normaliseForSearch(plain)
                            ));
                        }))
                        .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(futures);
            });
        }

        chain.handle((ignored, throwable) -> {
            List<GuideSearchEntry> finalEntries = new ArrayList<>(built);
            finalEntries.sort(Comparator
                    .comparingInt(GuideSearchEntry::pageIndex)
                    .thenComparingInt(GuideSearchEntry::entryIndex));

            synchronized (GuideSearchIndex.class) {
                if (!signature.equals(loadingSearchIndexSignature)) return null;

                searchIndex = List.copyOf(finalEntries);
                searchIndexSignature = signature;
                loadingSearchIndex = false;
                loadingSearchIndexSignature = "";
            }

            return null;
        });
    }

    /**
     * Searches the cached index and returns ranked navigable results.
     */
    public static List<GuideSearchResult> search(@Nullable GuideBook book, @Nullable String input) {
        if (book == null || input == null) return List.of();

        String query = normaliseForSearch(input);
        if (query.length() < 2) return List.of();

        List<GuideSearchEntry> localIndex = searchIndex;
        if (localIndex.isEmpty()) return List.of();

        Map<Long, GuideSearchResult> uniqueResults = new HashMap<>(Math.min(localIndex.size(), 64));

        for (GuideSearchEntry entry : localIndex) {

            int titlePos = entry.entryTitleSearch().indexOf(query);
            if (titlePos >= 0) {
                String snippet = buildSnippet(entry.entryTitle(), titlePos, query.length());
                insertBestCandidate(uniqueResults, new GuideSearchResult(
                        entry.pageIndex(),
                        entry.entryIndex(),
                        entry.pageTitle(),
                        entry.entryTitle(),
                        snippet,
                        0,
                        titlePos));
                continue;
            }

            int pageTitlePos = entry.pageTitleSearch().indexOf(query);
            if (pageTitlePos >= 0 && entry.entryIndex() == 0) {
                String snippet = buildSnippet(entry.pageTitle(), pageTitlePos, query.length());
                insertBestCandidate(uniqueResults, new GuideSearchResult(
                        entry.pageIndex(),
                        0,
                        entry.pageTitle(),
                        entry.entryTitle(),
                        snippet,
                        1,
                        pageTitlePos));
                continue;
            }

            int contentPos = entry.plainContentSearch().indexOf(query);
            if (contentPos >= 0) {
                String snippet = buildSnippet(entry.plainContent(), contentPos, query.length());
                insertBestCandidate(uniqueResults, new GuideSearchResult(
                        entry.pageIndex(),
                        entry.entryIndex(),
                        entry.pageTitle(),
                        entry.entryTitle(),
                        snippet,
                        2,
                        contentPos));
            }
        }

        var results = new ArrayList<>(uniqueResults.values());

        results.sort(Comparator
                .comparingInt(GuideSearchResult::matchRank)
                .thenComparingInt(GuideSearchResult::matchOffset)
                .thenComparing(GuideSearchResult::entryTitle, String.CASE_INSENSITIVE_ORDER));

        return results;
    }

    /**
     * Converts result payload objects into display labels consumed by SearchWidget.
     */
    public static String renderResult(Object obj) {

        if (obj instanceof GuideSearchResult result)
            return result.pageTitle() + " - " + result.entryTitle();

        return String.valueOf(obj);
    }

    /**
     * Converts result payload objects into tooltip content shown on hover.
     */
    @Nullable
    public static String renderTooltip(Object obj) {
        if (obj instanceof GuideSearchResult result) {

            return result.snippet();
        }
        return null;
    }

    private static void insertBestCandidate(Map<Long, GuideSearchResult> uniqueResults, GuideSearchResult candidate) {
        long key = (((long) candidate.pageIndex()) << 32) | (candidate.entryIndex() & 0xffffffffL);
        GuideSearchResult existing = uniqueResults.get(key);

        if (existing == null
                || candidate.matchRank() < existing.matchRank()
                || (candidate.matchRank() == existing.matchRank() && candidate.matchOffset() < existing.matchOffset())) {
            uniqueResults.put(key, candidate);
        }
    }

    private static List<SearchSeed> collectSearchSeeds(GuideBook book) {
        var seeds = new ArrayList<SearchSeed>();
        var pages = book.getPages();

        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            var page = pages.get(pageIndex);
            String pageTitle = collapseWhitespace(page.getTitle());
            String pageTitleSearch = normaliseForSearch(pageTitle);

            var entries = page.getEntries();
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                var entry = entries.get(entryIndex);
                String entryTitle = collapseWhitespace(entry.getTitle());
                seeds.add(new SearchSeed(
                        pageIndex,
                        entryIndex,
                        pageTitle,
                        entryTitle,
                        entry.getLink(),
                        pageTitleSearch,
                        normaliseForSearch(entryTitle)
                ));
            }
        }

        return seeds;
    }

    private static String buildSearchIndexSignature(GuideBook book) {
        String locale = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        StringBuilder signature = new StringBuilder(256).append(locale).append('|');

        var pages = book.getPages();
        for (var page : pages) {
            signature.append(collapseWhitespace(page.getTitle())).append('|');
            for (var entry : page.getEntries()) {
                signature.append(collapseWhitespace(entry.getTitle()))
                        .append('|')
                        .append(entry.getLink())
                        .append('|');
            }
        }

        return signature.toString();
    }

    private static String htmlToSearchablePlaintext(@Nullable String html) {
        if (html == null || html.isBlank()) return "";
        return collapseWhitespace(Jsoup.parse(html).text());
    }

    private static String buildSnippet(String source, int matchStart, int matchLength) {
        if (source == null || source.isBlank()) return "";
        if (matchStart < 0) return "..." + collapseWhitespace(source) + "...";

        int safeStart = Math.min(matchStart, source.length());
        int safeEnd = Math.max(safeStart, Math.min(safeStart + Math.max(1, matchLength), source.length()));

        int from = Math.max(0, safeStart - SEARCH_SNIPPET_RADIUS);
        int to = Math.min(source.length(), safeEnd + SEARCH_SNIPPET_RADIUS);

        while (from > 0 && !Character.isWhitespace(source.charAt(from - 1))) from--;
        while (to < source.length() && !Character.isWhitespace(source.charAt(to))) to++;

        String snippet = source.substring(from, to).trim();
        if (snippet.isEmpty()) return "";

        return "..." + snippet + "...";
    }

    private static String collapseWhitespace(@Nullable String value) {
        if (value == null || value.isBlank()) return "";
        return WHITESPACE_PATTERN.matcher(value).replaceAll(" ").trim();
    }

    private static String normaliseForSearch(@Nullable String value) {
        return collapseWhitespace(value).toLowerCase(Locale.ROOT);
    }
}


