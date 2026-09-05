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

package com.duom.ardamaps.api.locations;

import com.duom.ardamaps.core.data.Vec3d;

import java.util.List;

/**
 * Public location DTO for external mods.
 *
 * @param id          the location id
 * @param name        the location display name
 * @param world       the dimension id
 * @param types       location type/category names
 * @param warp        optional warp name
 * @param position    world position
 * @param pathfinder  optional pathfinder path/chapter reference
 * @param status      project status
 * @param regions     region names containing this location
 * @param canon       whether the location is canon
 * @param description location details text
 * @param externalUrl external details URL
 */
public record ApiLocation(String id,
                          String name,
                          String world,
                          List<String> types,
                          String warp,
                          Vec3d position,
                          String pathfinder,
                          String status,
                          List<String> regions,
                          boolean canon,
                          String description,
                          String externalUrl) {

}
