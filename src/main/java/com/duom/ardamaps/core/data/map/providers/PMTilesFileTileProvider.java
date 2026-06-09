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

import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.file.FileRangeReader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tile provider that reads map tiles from a PMTiles file.
 */
public class PMTilesFileTileProvider extends PMTilesProvider {

    /**
     * Create a PMTilesFileTileProvider from the specified PMTiles file path.
     * This constructor is expected to fail with an IOException if the file is inaccessible.
     *
     * @param filePath The path to the PMTiles file
     */
    public static TileProvider<PmTileKey> init(String filePath) throws IOException {

        var fileTileProvider = new PMTilesFileTileProvider();

        FileRangeReader rangeReader = FileRangeReader.builder()
                .path(Path.of(filePath))
                .build();

        RangeReader memoryCached = CachingRangeReader.builder(rangeReader)
                .withBlockAlignment()
                .build();

        fileTileProvider.configureReader(memoryCached);

        return fileTileProvider;
    }
}