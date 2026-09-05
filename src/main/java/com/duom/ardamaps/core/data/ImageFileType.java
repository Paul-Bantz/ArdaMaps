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

package com.duom.ardamaps.core.data;

import org.jetbrains.annotations.NotNull;

/**
 * Enumeration of supported image file types.
 */
public enum ImageFileType {

    WEBP,
    PNG,
    JPG,
    JPEG;

    /**
     * Converts a string file extension to the corresponding ImageFileType.
     *
     * @param fileExt The file extension as a string.
     * @return The corresponding ImageFileType. Defaults to PNG if unrecognized.
     */
    public static @NotNull ImageFileType fromString(String fileExt) {

        if (fileExt == null)
            return PNG;

        return switch (fileExt.toLowerCase()) {
            case "webp" -> WEBP;
            case "jpeg", "jpg" -> JPEG;
            default -> PNG;
        };
    }
}