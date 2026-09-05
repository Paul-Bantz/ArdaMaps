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

package com.duom.ardamaps.core.data.map.region;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizes region and location names for duplicate detection.
 */
public final class RegionNameMatcher {

    /** Parenthetical suffix matched at the end of a display name. */
    private static final String TRAILING_PARENTHETICAL = "\\s*\\([^)]*\\)\\s*$";

    /** Leading article removed after punctuation folding. */
    private static final String LEADING_ARTICLE = "the ";

    /**
     * Prevents utility class construction.
     */
    private RegionNameMatcher() {

    }

    /**
     * Normalizes a region or location name/id for comparison.
     *
     * @param value The source name or identifier.
     * @return The normalized value, or an empty string for null/blank input.
     */
    public static String normalize(String value) {

        if (value == null || value.isBlank()) return "";

        String normalized = value.strip()
                .replaceAll(TRAILING_PARENTHETICAL, "")
                .replace("æ", "ae")
                .replace("Æ", "AE");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip()
                .replaceAll("\\s+", " ");

        if (normalized.startsWith(LEADING_ARTICLE))
            normalized = normalized.substring(LEADING_ARTICLE.length()).strip();

        return normalized;
    }
}
