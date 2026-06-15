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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
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

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerExplorationPacket.class);

    /** Empty packet */
    public static final PlayerExplorationPacket EMPTY = new PlayerExplorationPacket("", "", List.of(), List.of());

    /**
     * Reads a PlayerExplorationPacket from a PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new PlayerExplorationPacket instance.
     */
    public static PlayerExplorationPacket read(PacketByteBuf buf) {

        PlayerExplorationPacket packet = PlayerExplorationPacket.EMPTY;
        String dimensionId = "";
        String regionId = "";
        List<List<Vec2d>> rootRegionPolygon = List.of();
        List<List<Vec2d>> regionPolygon = List.of();

        try {

            dimensionId = buf.readString();
            regionId = buf.readString();
            rootRegionPolygon = readPolygonFromBuffer(buf);
            regionPolygon = readPolygonFromBuffer(buf);

            packet = new PlayerExplorationPacket(dimensionId, regionId, rootRegionPolygon, regionPolygon);
            LOGGER.info("Read PlayerExplorationPacket for dimension '{}', parent region polygon with {} polygons, and region polygon with {} polygons.",
                    dimensionId, rootRegionPolygon.size(), regionPolygon.size());

        } catch (Throwable ex) {
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
    private static List<List<Vec2d>> readPolygonFromBuffer(PacketByteBuf buf) {

        int polygonCount = buf.readVarInt();

        if (polygonCount == 0)
            return List.of();

        List<List<Vec2d>> polygons = new ArrayList<>(polygonCount);

        for (int i = 0; i < polygonCount; i++) {
            int pointCount = buf.readVarInt();
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
     * Builds a PacketByteBuf from this PlayerExplorationPacket.
     *
     * @return A PacketByteBuf representing this packet.
     */
    @Override
    public PacketByteBuf build() {

        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(dimensionId);
        buf.writeString(regionId);
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
    private void writePolygonInBuffer(List<List<Vec2d>> polygonCollection, PacketByteBuf buf) {

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