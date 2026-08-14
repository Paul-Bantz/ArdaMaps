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

package com.duom.ardamaps.core.data.map.providers;

import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * HTTP fetch result with the cache metadata needed by the tile pipeline.
 *
 * @param bytes         Response body bytes.
 * @param status        HTTP status code.
 * @param lastModified  Last-Modified header value, if present.
 * @param maxAgeSeconds Clamped Cache-Control max-age.
 */
record FetchResult(byte[] bytes, int status, String lastModified, long maxAgeSeconds) {

    static final long DEFAULT_TILE_MAX_AGE_SECONDS = 86_400L;
    static final long DEFAULT_ABSENT_MAX_AGE_SECONDS = 14_400L;
    static final long MIN_MAX_AGE_SECONDS = 300L;
    static final long MAX_MAX_AGE_SECONDS = 604_800L;

    /**
     * Checks if this fetch result represents an absent tile (no content, not found, or empty).
     *
     * @return {@code true} if the tile is absent (status 204/404 or zero-length response).
     */
    boolean isAbsent() {
        return status == 204 || status == 404 || bytes.length == 0;
    }

    /**
     * Checks if this fetch result represents a 304 Not Modified response.
     *
     * @return {@code true} if the status code is 304.
     */
    boolean isNotModified() {
        return status == 304;
    }

    /**
     * Constructs a FetchResult from an HttpResponse, extracting cache metadata and clamping TTLs.
     *
     * @param response The HTTP response containing status, headers, and body.
     * @return A FetchResult with parsed cache control, last-modified, and clamped max-age.
     */
    static FetchResult fromResponse(HttpResponse<byte[]> response) {

        int status = response.statusCode();
        byte[] body = response.body() == null ? new byte[0] : response.body();
        Optional<String> contentLength = response.headers().firstValue("content-length");
        boolean emptyContentLength = contentLength
                .map(value -> {
                    try {
                        return Long.parseLong(value) == 0L;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                })
                .orElse(false);

        if (status == 204 || emptyContentLength) body = new byte[0];

        long defaultMaxAge = status == 204 || status == 404 || body.length == 0
                ? DEFAULT_ABSENT_MAX_AGE_SECONDS
                : DEFAULT_TILE_MAX_AGE_SECONDS;

        return new FetchResult(
                body,
                status,
                response.headers().firstValue("last-modified").orElse(null),
                parseMaxAge(response.headers().firstValue("cache-control").orElse(null), defaultMaxAge));
    }

    /**
     * Parses the max-age directive from a Cache-Control header.
     *
     * @param cacheControl The Cache-Control header value, or null/blank to use the default.
     * @param defaultSeconds The default max-age to use if the header is absent or unparseable.
     * @return The parsed max-age clamped to {@link #MIN_MAX_AGE_SECONDS} and {@link #MAX_MAX_AGE_SECONDS}.
     */
    static long parseMaxAge(String cacheControl, long defaultSeconds) {

        if (cacheControl == null || cacheControl.isBlank()) return clampMaxAge(defaultSeconds);

        for (String directive : cacheControl.split(",")) {
            String trimmed = directive.trim();
            if (!trimmed.regionMatches(true, 0, "max-age=", 0, "max-age=".length())) continue;

            try {
                return clampMaxAge(Long.parseLong(trimmed.substring("max-age=".length()).trim()));
            } catch (NumberFormatException ignored) {
                return clampMaxAge(defaultSeconds);
            }
        }

        return clampMaxAge(defaultSeconds);
    }

    /**
     * Clamps a max-age value to the safe retry window.
     *
     * @param seconds The seconds value to clamp.
     * @return The clamped value between {@link #MIN_MAX_AGE_SECONDS} and {@link #MAX_MAX_AGE_SECONDS}.
     */
    private static long clampMaxAge(long seconds) {

        return Math.clamp(seconds, MIN_MAX_AGE_SECONDS, MAX_MAX_AGE_SECONDS);
    }
}
