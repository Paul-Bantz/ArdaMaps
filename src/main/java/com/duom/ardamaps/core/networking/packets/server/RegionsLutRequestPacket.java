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

package com.duom.ardamaps.core.networking.packets.server;

import com.duom.ardamaps.core.consumers.networking.IPacket;
import com.duom.ardamaps.core.consumers.networking.IRespondablePacket;
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.Date;
import java.util.UUID;

/**
 * A packet sent from the client to the server requesting region lookup texture data.
 *
 * @param date The date of the last update client side.
 */
public record RegionsLutRequestPacket(UUID requestId, Date date) implements IRespondablePacket<RegionsLutRequestPacket> {
    public static final CustomPacketPayload.Type<RegionsLutRequestPacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("regions_lut_data_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionsLutRequestPacket> CODEC = IPacket.codec(RegionsLutRequestPacket::read);

    public RegionsLutRequestPacket(Date date) {
        this(new UUID(0L, 0L), date);
    }

    /**
     * Reads a RegionsLutRequestPacket from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new RegionsLutRequestPacket instance.
     */
    public static RegionsLutRequestPacket read(FriendlyByteBuf buf) {

        var requestId = buf.readUUID();
        var hasData = buf.readBoolean();
        Date updateDate = null;
        if (hasData) updateDate = new Date(buf.readLong());

        return new RegionsLutRequestPacket(requestId, updateDate);
    }

    /**
     * Builds a PacketByteBuf from this RegionsLutRequestPacket.
     *
     * @return A PacketByteBuf representing this LocationsRequestPacket.
     */
    @Override
    public FriendlyByteBuf build() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(requestId);
        buf.writeBoolean(date != null);

        if (date != null) buf.writeLong(date.getTime());

        return buf;
    }

    @Override
    public RegionsLutRequestPacket withRequestId(UUID requestId) {
        return new RegionsLutRequestPacket(requestId, date);
    }

    @Override
    public CustomPacketPayload.Type<RegionsLutRequestPacket> type() {
        return TYPE;
    }
}
