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
import java.util.List;
import java.util.Map;

/**
 * HTTP fetch result with the cache metadata needed by the tile pipeline.
 *
 * @param bytes         response body bytes.
 * @param status        HTTP status code.
 * @param lastModified  Last-Modified header value, if present.
 * @param maxAgeSeconds clamped Cache-Control max-age.
 */
record FetchResult(byte[] bytes, int status, String lastModified, long maxAgeSeconds) {

    /** Default cache lifetime for successful tile responses. */
    static final long DEFAULT_TILE_MAX_AGE_SECONDS = 86_400L;
    /** Default cache lifetime for absent tile responses. */
    static final long DEFAULT_ABSENT_MAX_AGE_SECONDS = 14_400L;
    /** Lower bound for parsed cache lifetimes. */
    static final long MIN_MAX_AGE_SECONDS = 300L;
    /** Upper bound for parsed cache lifetimes. */
    static final long MAX_MAX_AGE_SECONDS = 604_800L;

    /**
     * Return whether the response represents an absent tile.
     *
     * @return true for 204/404/empty-body responses.
     */
    boolean isAbsent() {
        return status == 204 || status == 404 || bytes.length == 0;
    }

    /**
     * Return whether the response indicates a not-modified cache hit.
     *
     * @return true when the status code is 304.
     */
    boolean isNotModified() {
        return status == 304;
    }

    /**
     * Build a fetch result from an HTTP client response.
     *
     * @param response HTTP response to adapt.
     * @return Adapted fetch result.
     */
    static FetchResult fromResponse(HttpResponse<byte[]> response) {

        int status = response.statusCode();
        byte[] body = response.body() == null ? new byte[0] : response.body();
        String contentLength = response.headers().firstValue("content-length").orElse(null);
        boolean emptyContentLength = false;
        if (contentLength != null) {
            try {
                emptyContentLength = Long.parseLong(contentLength) == 0L;
            } catch (NumberFormatException ignored) {
            }
        }

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
     * Build a fetch result from a lower-level connection response.
     *
     * @param status HTTP status code.
     * @param body Response body bytes.
     * @param headers Response headers.
     * @return Adapted fetch result.
     */
    static FetchResult fromConnection(int status, byte[] body, Map<String, List<String>> headers) {

        byte[] bytes = body == null ? new byte[0] : body;
        String contentLength = firstHeader(headers, "content-length");
        boolean emptyContentLength = false;
        if (contentLength != null) {
            try {
                emptyContentLength = Long.parseLong(contentLength) == 0L;
            } catch (NumberFormatException ignored) {
            }
        }

        if (status == 204 || emptyContentLength) bytes = new byte[0];

        long defaultMaxAge = status == 204
                || status == 404
                || bytes.length == 0
                ? DEFAULT_ABSENT_MAX_AGE_SECONDS
                : DEFAULT_TILE_MAX_AGE_SECONDS;

        return new FetchResult(
                bytes,
                status,
                firstHeader(headers, "last-modified"),
                parseMaxAge(firstHeader(headers, "cache-control"), defaultMaxAge));
    }

    /**
     * Parse and clamp a Cache-Control max-age value.
     *
     * @param cacheControl Cache-Control header value.
     * @param defaultSeconds Fallback lifetime when no max-age is present.
     * @return Parsed and clamped lifetime in seconds.
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
     * Clamp a cache lifetime to the supported range.
     *
     * @param seconds Cache lifetime in seconds.
     * @return Clamped lifetime.
     */
    private static long clampMaxAge(long seconds) {

        return Math.max(MIN_MAX_AGE_SECONDS, Math.min(MAX_MAX_AGE_SECONDS, seconds));
    }

    /**
     * Read the first matching header value from a case-insensitive header map.
     *
     * @param headers Header map.
     * @param name Header name to look up.
     * @return First matching value, or null.
     */
    private static String firstHeader(Map<String, List<String>> headers, String name) {

        if (headers == null) return null;

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(name)) continue;
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        }

        return null;
    }
}
