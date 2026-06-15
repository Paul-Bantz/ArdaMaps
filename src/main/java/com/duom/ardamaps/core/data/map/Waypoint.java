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

package com.duom.ardamaps.core.data.map;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.gui.ModConstants;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a waypoint on the map
 *
 * @param x          the x position of the waypoint
 * @param z          the z position of the waypoint
 * @param text       the waypoint's display text
 * @param r          the Red component of the waypoint colour
 * @param g          the Green component of the waypoint colour
 * @param b          the Blue component of the waypoint colour
 * @param identifier the waypoint's initiator's identifier this attributes defines the equality between waypoints.
 * @param dimension  this waypoint's dimension
 * @param showToast  whether to display a toast notification when the waypoint is hit
 * @param icon       the waypoint's icon
 */
public record Waypoint(int x, int z,
                       String text,
                       float r, float g, float b,
                       String identifier, String dimension,
                       boolean showToast,
                       Identifier icon) {

    /**
     * Initializes a new default waypoint with colour yellow at the given position
     *
     * @param x         the X coordinate
     * @param z         the Y coordinate
     * @param dimension the dimension of the waypoint
     */
    public Waypoint(int x, int z, String dimension) {

        this(x, z,
                String.format("x:%d, z:%d", x, z),
                1f, 0.7058f, 0f,                    // Default Yellow colour
                ArdaMaps.MOD_ID, dimension);
    }

    /**
     *
     * @param x          the x position of the waypoint
     * @param z          the z position of the waypoint
     * @param text       the waypoint's display text
     * @param r          the Red component of the waypoint colour
     * @param g          the Green component of the waypoint colour
     * @param b          the Blue component of the waypoint colour
     * @param identifier the waypoint's initiator's identifier this attributes defines the equality between waypoints.
     * @param dimension  this waypoint's dimension
     */
    public Waypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension) {

        this(x, z,
                text,
                r, g, b,
                identifier, dimension,
                true, ModConstants.ICON_WAYPOINT);
    }

    /**
     * Returns the JSON String as a valid waypoint or empty
     *
     * @param json the waypoint JSON string
     * @return an optional containing the waypoint if the JSON was valid, otherwise an empty optional
     */
    public static Optional<Waypoint> fromJson(@NonNull String json) {

        Waypoint waypoint = null;

        try {

            Gson gson = new Gson();
            Waypoint deserialized = gson.fromJson(json, Waypoint.class);

            if (deserialized != null) {

                waypoint = new Waypoint(
                        deserialized.x,
                        deserialized.z,
                        deserialized.text !=  null ? deserialized.text : "",
                        deserialized.r,
                        deserialized.g,
                        deserialized.b,
                        deserialized.identifier != null ? deserialized.identifier : ArdaMaps.MOD_ID,
                        deserialized.dimension != null ? deserialized.dimension : Client.currentDimensionId(),
                        deserialized.showToast,
                        deserialized.icon != null ? deserialized.icon : ModConstants.ICON_WAYPOINT
                );
            }

            return Optional.ofNullable(waypoint);

        } catch (Exception ex) {
            // Silently ignore any parsing errors and return an empty optional
        }

        return Optional.empty();
    }

    /**
     * Serializes this waypoint to gson
     *
     * @param waypoint the waypoint to convert
     * @return the corresponding JSON String
     */
    public static String toJson(@NonNull Waypoint waypoint) {

        Gson gson = new GsonBuilder() .addSerializationExclusionStrategy(new ExclusionStrategy() {

            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return field.getName().equals("showToast")
                        || field.getName().equals("dimension")
                        || field.getName().equals("icon");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) { return false; }
        }).create();
        return gson.toJson(waypoint);
    }

    /**
     * Creates a duplicate of the waypoint in parameter
     *
     * @param waypoint the waypoint to copy
     * @return the copy of the waypoint
     */
    public static Waypoint copy(Waypoint waypoint) {

        return new Waypoint(waypoint.x, waypoint.z,
                waypoint.text,
                waypoint.r, waypoint.g, waypoint.b,
                waypoint.identifier, waypoint.dimension,
                waypoint.showToast, waypoint.icon);
    }

    /**
     * @return this waypoints position as a @{link {@link Vec2d}}
     */
    public Vec2d getPosition() {
        return new Vec2d(x, z);
    }

    /**
     * @return the waypoint display String, "Waypoint [x, z]:\"text\""
     */
    @Override
    public @NonNull String toString() {

        return String.format("Waypoint [%d, %d]:\"%s\"", x, z, text);
    }

    /**
     * Waypoints are considered equal if their identifiers are equal.
     *
     * @param obj the reference object with which to compare.
     * @return true if the parameter is a Waypoint and its identifier matches this.identifier
     */
    @Override
    public boolean equals(Object obj) {

        return obj instanceof Waypoint
                && Objects.equals(this.identifier, ((Waypoint) obj).identifier)
                && Objects.equals(this.dimension, ((Waypoint) obj).dimension)
                && x == ((Waypoint) obj).x && z == ((Waypoint) obj).z;
    }

    /**
     * @return the hash of this waypoint
     */
    @Override
    public int hashCode() {
        return Objects.hash(identifier, dimension, x, z);
    }
}
