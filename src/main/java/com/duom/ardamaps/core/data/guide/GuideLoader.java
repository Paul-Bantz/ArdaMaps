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

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.Client;
import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for loading guide data from the mod's resource pack.
 * All IO is performed asynchronously on {@link ArdaMaps#IO_EXECUTOR}.
 */
public final class GuideLoader {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideLoader.class);

    /** Default fallback locale */
    private static final String DEFAULT_LOCALE = "en_us";

    /** Identifier for the guide index JSON file. */
    private static final Identifier GUIDE_JSON_ID = new Identifier(ArdaMaps.MOD_ID, "guide/guide.json");

    /** Gson instance shared for guide deserialization. */
    private static final Gson GSON = new Gson();

    private GuideLoader() { /* utility class */ }

    /**
     * Gets the current client locale, updated dynamically to reflect language changes.
     *
     * @return the current client locale
     */
    private static String getClientLocale() {
        return Client.mc().getLanguageManager().getLanguage();
    }

    /**
     * Loads and deserializes {@code assets/ardamaps/guide/guide.json} from the active resource manager.
     *
     * @return a {@link CompletableFuture} that resolves to the parsed {@link GuideBook},
     * or an empty {@link GuideBook} if loading fails
     */
    public static CompletableFuture<GuideBook> loadGuideBook() {

        return CompletableFuture.supplyAsync(() -> {

            var resourceManager = Client.mc().getResourceManager();

            Map<String, String> titles = loadTitles(resourceManager);
            GuideBook guideBook = loadGuide(resourceManager);

            return resolveTitles(guideBook, titles);

        }, ArdaMaps.IO_EXECUTOR);
    }

    /**
     * Loads a guide content based on the client locale. Falls back to default locale if not found.
     *
     * @param resourceManager the resource manager
     * @return the loaded book
     */
    private static GuideBook loadGuide(ResourceManager resourceManager) {

        try {
            Optional<Resource> resource = resourceManager.getResource(GUIDE_JSON_ID);

            if (resource.isEmpty()) {

                LOGGER.error("[GuideLoader] guide.json not found at {}", GUIDE_JSON_ID);
                return new GuideBook();
            }

            try (Reader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {

                return GSON.fromJson(reader, GuideBook.class);
            }

        } catch (Exception e) {

            LOGGER.error("[GuideLoader] Failed to load guide.json", e);
            return new GuideBook();
        }
    }

    /**
     * Resolves and binds localized titles to the guide book's sections and entries based on the provided titles map.
     *
     * @param book   the book to perform the resolution on
     * @param titles the map of titles keyed by their IDs
     * @return the resolved book
     */
    private static GuideBook resolveTitles(GuideBook book, Map<String, String> titles) {

        if (book != null) {

            for (GuidePage guidePage : book.getPages()) {

                var pageTitle = titles.getOrDefault(guidePage.getId(), guidePage.getId());
                guidePage.setTitle(pageTitle);

                for (GuideEntry guideEntry : guidePage.getEntries()) {

                    var entryTitle = titles.getOrDefault(guideEntry.getId(), guideEntry.getId());
                    guideEntry.setTitle(entryTitle);
                }
            }

            return book;
        }

        return new GuideBook();
    }

    /**
     * Resolves a locale resource with a three-tier fallback strategy:
     * 1. Try to load the exact client locale (e.g. guide/fr_ca/titles.json)
     * 2. If not found, try to find any resource matching the language prefix (e.g. guide/fr_fr/titles.json for fr_ca)
     * 3. If still not found, fall back to the default locale (e.g. guide/en_us/titles.json)
     *
     * @param resourceManager the resource manager to search
     * @param filePath        the relative file path within the guide directory (e.g. titles.json or entries/explore/overview.html)
     * @return an Optional containing the resource if found, empty otherwise
     */
    private static Optional<Resource> resolveLocale(ResourceManager resourceManager, String filePath) {

        String clientLocale = getClientLocale();

        // Try exact client locale
        var identifier = new Identifier(ArdaMaps.MOD_ID, String.format("guide/%s/%s", clientLocale, filePath));
        var resource = resourceManager.getResource(identifier);

        if (resource.isPresent()) {
            return resource;
        }

        // Extract language prefix (e.g. "fr" from "fr_ca") and look for any matching locale
        String[] localeParts = clientLocale.split("_");

        if (localeParts.length > 0) {
            String languagePrefix = localeParts[0];
            String guidePath = "guide";

            try {
                var resourceMap = resourceManager.findResources(guidePath, id -> {

                    String path = id.getPath();

                    // Check if this resource matches the pattern: guide/<language>_*/<filePath>
                    if (path.startsWith(guidePath + "/")) {

                        String relativePath = path.substring((guidePath + "/").length());
                        String[] parts = relativePath.split("/");

                        if (parts.length >= 2) {

                            String localeFolder = parts[0];
                            String[] localeFolderParts = localeFolder.split("_");

                            if (localeFolderParts.length > 0 && localeFolderParts[0].equals(languagePrefix)) {

                                // Check if the rest of the path matches the filePath
                                String resourceFilePath = String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                                return resourceFilePath.equals(filePath);
                            }
                        }
                    }
                    return false;
                });

                if (!resourceMap.isEmpty()) {
                    Identifier foundId = resourceMap.keySet().iterator().next();
                    return resourceManager.getResource(foundId);
                }
            } catch (Exception e) {

                LOGGER.debug("[GuideLoader] Error searching for language prefix resources", e);
            }
        }

        // If nothing found fall back to default locale
        identifier = new Identifier(ArdaMaps.MOD_ID, String.format("guide/%s/%s", DEFAULT_LOCALE, filePath));

        return resourceManager.getResource(identifier);
    }

    /**
     * Loads the pages and entries titles with the correct locale : either client locale if available or default if none found
     *
     * @param resourceManager the resource manager
     * @return the titles keyed by their IDs
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> loadTitles(ResourceManager resourceManager) {

        Map<String, String> titles = Map.of();
        String filePath = "titles.json";

        Optional<Resource> resource = resolveLocale(resourceManager, filePath);

        if (resource.isEmpty()) {

            LOGGER.warn("[GuideLoader] titles definition not found for any locale - tried client locale, language prefix, and default locale");
            return titles;
        }

        try (Reader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {

            titles = GSON.fromJson(reader, Map.class);
        } catch (Exception e) {

            LOGGER.error("[GuideLoader] Failed to load titles.json", e);
        }

        return titles;
    }

    /**
     * Loads the raw HTML content for a guide entry identified by its relative link path,
     * e.g. {@code entries/explore/overview.html}.
     *
     * @param link the relative path as stored in {@link GuideEntry#getLink()}
     * @return a {@link CompletableFuture} that resolves to the raw HTML string,
     * or an empty string if loading fails
     */
    public static CompletableFuture<String> loadHtml(String link) {

        return CompletableFuture.supplyAsync(() -> {

            if (link == null || link.isBlank()) return "";

            try {
                var resourceManager = MinecraftClient.getInstance().getResourceManager();
                Optional<Resource> resource = resolveLocale(resourceManager, link);

                if (resource.isEmpty()) {

                    LOGGER.error("[GuideLoader] HTML resource not found: {} - tried client locale, language prefix, and default locale", link);
                    return "";
                }

                try (var stream = resource.get().getInputStream()) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }

            } catch (Exception e) {
                LOGGER.error("[GuideLoader] Failed to load HTML: {}", link, e);
                return "";
            }
        }, ArdaMaps.IO_EXECUTOR);
    }
}
