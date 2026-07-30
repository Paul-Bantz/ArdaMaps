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
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Packet sent from server to client containing information about a player's exploration of a region.
 *
 * @param dimensionId         The dimension this region belongs to (e.g. {@code "minecraft:overworld"}).
 * @param regionId            The identifier of the discovered region
 * @param parentRegionPolygon A list of regionPolygon representing the full parent region polygon
 * @param regionPolygon       A list of regionPolygon representing explored areas within the region.
 */
public record PlayerExplorationPacket(String dimensionId,
                                      String regionId,
                                      List<List<Vec2d>> parentRegionPolygon,
                                      List<List<Vec2d>> regionPolygon) implements IPacket {
    public static final CustomPacketPayload.Type<PlayerExplorationPacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("player_exploration_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerExplorationPacket> CODEC = IPacket.codec(PlayerExplorationPacket::read);

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerExplorationPacket.class);

    /** Maximum number of polygons accepted in one polygon collection. */
    private static final int MAX_POLYGONS = 1024;

    /** Maximum number of points accepted in a single polygon. */
    private static final int MAX_POINTS_PER_POLYGON = 16_384;

    /** Empty packet */
    public static final PlayerExplorationPacket EMPTY = new PlayerExplorationPacket("", "", List.of(), List.of());

    /**
     * Reads a PlayerExplorationPacket from a PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new PlayerExplorationPacket instance.
     */
    public static PlayerExplorationPacket read(FriendlyByteBuf buf) {

        PlayerExplorationPacket packet = PlayerExplorationPacket.EMPTY;
        String dimensionId = "";
        String regionId = "";
        List<List<Vec2d>> rootRegionPolygon = List.of();
        List<List<Vec2d>> regionPolygon = List.of();

        try {

            dimensionId = buf.readUtf();
            regionId = buf.readUtf();
            rootRegionPolygon = readPolygonFromBuffer(buf);
            regionPolygon = readPolygonFromBuffer(buf);

            packet = new PlayerExplorationPacket(dimensionId, regionId, rootRegionPolygon, regionPolygon);
            LOGGER.info("Read PlayerExplorationPacket for dimension '{}', parent region polygon with {} polygons, and region polygon with {} polygons.",
                    dimensionId, rootRegionPolygon.size(), regionPolygon.size());

        } catch (RuntimeException ex) {
            /*
                Catch-all to prevent client crashes due to malformed packets. It can happen at client Server-join if
                erroneous data is returned from ArdaRegions API.
             */
            LOGGER.error("Error reading PlayerExplorationPacket - bad packet [dimension:{}, region:{}, rootRegionSize:{}, regionSize:{}]",
                    dimensionId, regionId, rootRegionPolygon.size(), regionPolygon.size(), ex);
        }

        return packet;
    }

    /**
     * Reads a list of polygons from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A list of polygons, where each polygon is a list of Vec2d points.
     */
    private static List<List<Vec2d>> readPolygonFromBuffer(FriendlyByteBuf buf) {

        int polygonCount = readCount(buf, "polygon", MAX_POLYGONS);

        if (polygonCount == 0)
            return List.of();

        List<List<Vec2d>> polygons = new ArrayList<>(polygonCount);

        for (int i = 0; i < polygonCount; i++) {
            int pointCount = readCount(buf, "polygon point", MAX_POINTS_PER_POLYGON);
            List<Vec2d> polygon = new ArrayList<>(pointCount);

            for (int j = 0; j < pointCount; j++) {

                var x = buf.readDouble();
                var y = buf.readDouble();
                polygon.add(new Vec2d(x, y));
            }
            polygons.add(polygon);
        }

        return polygons;
    }

    /**
     * Reads and validates a VarInt collection size from the packet.
     *
     * @param buf   The packet buffer.
     * @param label Human-readable field label for error messages.
     * @param max   Maximum accepted count.
     * @return The validated count.
     */
    private static int readCount(FriendlyByteBuf buf, String label, int max) {

        int count = buf.readVarInt();

        if (count < 0) {
            throw new IllegalArgumentException("Player exploration " + label + " count cannot be negative: " + count);
        }

        if (count > max) {
            throw new IllegalArgumentException("Player exploration " + label + " count exceeds maximum of " + max + ": " + count);
        }

        return count;
    }

    /**
     * Builds a PacketByteBuf from this PlayerExplorationPacket.
     *
     * @return A PacketByteBuf representing this packet.
     */
    @Override
    public FriendlyByteBuf build() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();

        buf.writeUtf(dimensionId);
        buf.writeUtf(regionId);
        writePolygonInBuffer(parentRegionPolygon, buf);
        writePolygonInBuffer(regionPolygon, buf);

        return buf;
    }

    /**
     * Writes a list of polygons to the given PacketByteBuf.
     *
     * @param polygonCollection The list of polygons to write, where each polygon is a list of Vec2d points.
     * @param buf               The PacketByteBuf to write to.
     */
    private void writePolygonInBuffer(List<List<Vec2d>> polygonCollection, FriendlyByteBuf buf) {

        if (polygonCollection == null || polygonCollection.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }

        buf.writeVarInt(polygonCollection.size());

        for (List<Vec2d> polygon : polygonCollection) {

            if (polygon == null || polygon.isEmpty()) {
                buf.writeVarInt(0);
                continue;
            }

            buf.writeVarInt(polygon.size());
            for (Vec2d vec : polygon) {
                buf.writeDouble(vec.x());
                buf.writeDouble(vec.y());
            }
        }
    }

    /**
     * @return true if this is an empty packet
     */
    public boolean isEmpty(){

        return this.equals(PlayerExplorationPacket.EMPTY);
    }

    @Override
    public CustomPacketPayload.Type<PlayerExplorationPacket> type() {
        return TYPE;
    }

    /**
     * @return this packets hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, regionId, parentRegionPolygon, regionPolygon);
    }

    /**
     * @param obj   the reference object with which to compare.
     * @return true if the objects are equals, false otherwise
     */
    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof PlayerExplorationPacket that)) return false;

        return Objects.equals(dimensionId, that.dimensionId) &&
                Objects.equals(regionId, that.regionId) &&
                Objects.equals(parentRegionPolygon, that.parentRegionPolygon) &&
                Objects.equals(regionPolygon, that.regionPolygon);
    }
}
