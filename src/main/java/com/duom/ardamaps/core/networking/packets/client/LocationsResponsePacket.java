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

package com.duom.ardamaps.core.networking.packets.client;

import com.duom.ardamaps.core.consumers.networking.IPacket;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.LocationClient;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A packet representing a response containing location data in JSON format.
 */
public record LocationsResponsePacket(LocationConfig<LocationClient> data) implements IPacket {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsResponsePacket.class);

    public static final LocationsResponsePacket EMPTY = new LocationsResponsePacket(null);

    /**
     * Reads a MapSourceResponsePacket - ie a timestamped list of location data within the world.
     *
     * @param buf The PacketByteBuf to read from
     * @return The MapSourceResponsePacket read from the buffer
     */
    public static LocationsResponsePacket read(PacketByteBuf buf) {

        var dataLength = buf.readInt();

        if (dataLength != 0) {

            LocationConfig<LocationClient> locationsConfig = new LocationConfig<>();
            byte[] compressedData = new byte[dataLength];
            buf.readBytes(compressedData);

            try {

                ByteArrayInputStream outputStream = new ByteArrayInputStream(compressedData);

                try (GZIPInputStream gzip = new GZIPInputStream(outputStream)) {
                    var serializedData = gzip.readAllBytes();
                    locationsConfig = SerializationUtils.deserialize(serializedData);
                } catch (SerializationException serializationException) {

                    throw new IllegalStateException("Unable to read location configuration. Is the server running the same version of ArdaMaps as the client?", serializationException);
                }

            } catch (IOException e) {

                LOGGER.error("Error decompressing location data from network packet", e);
            }

            return new LocationsResponsePacket(locationsConfig);
        }

        return LocationsResponsePacket.EMPTY;
    }

    /**
     * Builds a serialized and compressed PacketByteBuf representing the location data.
     *
     * @return The PacketByteBuf representing this packet
     */
    @Override
    public PacketByteBuf build() {
        PacketByteBuf buf = PacketByteBufs.create();

        var hasData = data != null && data.getLastUpdate() != null;

        if (hasData) {

            byte[] serializedData = SerializationUtils.serialize(data);

            try {

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                    gzip.write(serializedData);
                }

                var compressedData = outputStream.toByteArray();

                buf.writeInt(compressedData.length);
                buf.writeBytes(compressedData);

            } catch (IOException e) {

                LOGGER.error("Error compressing location data for network packet", e);
            }
        } else {
            buf.writeInt(0);
        }

        return buf;
    }
}