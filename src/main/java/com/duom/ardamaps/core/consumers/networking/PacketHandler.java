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

package com.duom.ardamaps.core.consumers.networking;

import com.duom.ardamaps.ArdaMaps;
import net.minecraft.util.Identifier;

/**
 * Abstract base class for packet handlers, providing common functionality.
 * <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class PacketHandler implements IPacketHandler {
    /** The unique identifier for the packet channel, constructed using the mod ID and a specific channel name. */
    private final Identifier channelId;

    /**
     * Constructs a new PacketHandler with the specified channel name.
     *
     * @param channelId The name of the packet channel, which will be combined with the mod ID to create a unique Identifier.
     */
    public PacketHandler(final String channelId) {
        this.channelId = Identifier.of(ArdaMaps.MOD_ID, channelId);
    }

    /**
     * @return The unique Identifier for the packet channel that this handler is responsible for. This Identifier is used to register the handler and send packets on the correct channel.
     */
    @Override
    public Identifier getChannelId() {
        return channelId;
    }
}