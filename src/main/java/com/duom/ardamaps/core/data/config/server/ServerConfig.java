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

package com.duom.ardamaps.core.data.config.server;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.shared.Configuration;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Server configuration class
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServerConfig extends Configuration<LocationServer> {

    /** the list of dimension definitions available on the server. */
    @SerializedName("dimensions")
    private List<Dimension> dimensions;

    /** CRON expression (Unix/Posix 5-field) defining when to automatically refresh locations from the active LocationSource */
    @SerializedName("refresh_cron")
    private String refreshCron;

    /** Whether to automatically generate missing dimensions based on server dimension data. */
    @SerializedName("auto_genen_missing_dimensions")
    private boolean autoGenerateMissingDimensions = false;
}