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
import com.duom.ardamaps.core.consumers.networking.IRespondablePacket;
import com.duom.ardamaps.core.data.config.ConfigManager;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.gui.ModConstants;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A packet representing a response containing location data in JSON format.
 */
public record LocationsResponsePacket(UUID requestId,
                                      LocationConfig<LocationClient> data) implements IRespondablePacket<LocationsResponsePacket> {

    public static final CustomPacketPayload.Type<LocationsResponsePacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("location_data_response"));

    public static final LocationsResponsePacket EMPTY = new LocationsResponsePacket(null);

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsResponsePacket.class);

    /** Maximum compressed location payload accepted from the wire. */
    private static final int MAX_COMPRESSED_DATA_LENGTH = 8 * 1024 * 1024;

    /** Location config type token preserving the LocationClient generic parameter. */
    private static final java.lang.reflect.Type LOCATION_CONFIG_TYPE = new TypeToken<LocationConfig<LocationClient>>() {
    }.getType();

    public static final StreamCodec<RegistryFriendlyByteBuf, LocationsResponsePacket> CODEC = IPacket.codec(LocationsResponsePacket::read);

    /**
     * Constructs a LocationsResponsePacket with the given location data.
     *
     * @param data The location configuration data to include in the response, or null for an empty response.
     */
    public LocationsResponsePacket(LocationConfig<LocationClient> data) {
        this(new UUID(0L, 0L), data);
    }

    /**
     * Reads a LocationsResponsePacket containing timestamped location data.
     *
     * @param buf The PacketByteBuf to read from.
     * @return The LocationsResponsePacket read from the buffer.
     */
    public static LocationsResponsePacket read(FriendlyByteBuf buf) {

        var requestId = buf.readUUID();
        var dataLength = buf.readInt();

        if (dataLength != 0) {

            validateDataLength(dataLength, buf.readableBytes());
            byte[] compressedData = new byte[dataLength];
            buf.readBytes(compressedData);

            try {

                ByteArrayInputStream outputStream = new ByteArrayInputStream(compressedData);

                try (GZIPInputStream gzip = new GZIPInputStream(outputStream)) {
                    var json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
                    LocationConfig<LocationClient> locationsConfig = ConfigManager.gson().fromJson(json, LOCATION_CONFIG_TYPE);
                    return new LocationsResponsePacket(requestId, locationsConfig);
                }

            } catch (IOException | JsonSyntaxException e) {

                throw new IllegalStateException("Unable to read location configuration. Is the server running the same version of ArdaMaps as the client?", e);
            }
        }

        return new LocationsResponsePacket(requestId, null);
    }

    /**
     * Validates the compressed payload size before allocating the target byte array.
     * <p>
     * Ensures the data length is non-negative, does not exceed the maximum, and fits within remaining buffer bytes.
     *
     * @param dataLength    The declared compressed payload length in bytes.
     * @param readableBytes The remaining readable bytes in the packet buffer.
     * @throws IllegalArgumentException If length is negative, exceeds maximum, or exceeds readable bytes.
     */
    private static void validateDataLength(int dataLength, int readableBytes) {

        if (dataLength < 0) {
            throw new IllegalArgumentException("Location response data length cannot be negative: " + dataLength);
        }

        if (dataLength > MAX_COMPRESSED_DATA_LENGTH) {
            throw new IllegalArgumentException("Location response data length exceeds maximum of "
                    + MAX_COMPRESSED_DATA_LENGTH + " bytes: " + dataLength);
        }

        if (dataLength > readableBytes) {
            throw new IllegalArgumentException("Location response data length " + dataLength
                    + " exceeds readable packet bytes " + readableBytes);
        }
    }

    /**
     * Serializes this packet into a compressed PacketByteBuf for transmission over the network.
     *
     * @return The PacketByteBuf representing this packet.
     */
    @Override
    public FriendlyByteBuf build() {
        FriendlyByteBuf buf = FriendlyByteBufs.create();

        buf.writeUUID(requestId);
        var hasData = data != null && data.getLastUpdate() != null;

        if (hasData) {

            byte[] serializedData = ConfigManager.gson().toJson(data, LOCATION_CONFIG_TYPE).getBytes(StandardCharsets.UTF_8);

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

    /**
     * Creates a new LocationsResponsePacket with the specified request identifier.
     *
     * @param requestId The request identifier to associate with this response.
     * @return A new LocationsResponsePacket with the updated request identifier.
     */
    @Override
    public LocationsResponsePacket withRequestId(UUID requestId) {
        return new LocationsResponsePacket(requestId, data);
    }

    @Override
    public CustomPacketPayload.@NonNull Type<LocationsResponsePacket> type() {
        return TYPE;
    }
}
