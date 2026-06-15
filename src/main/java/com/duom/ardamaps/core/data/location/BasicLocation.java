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

package com.duom.ardamaps.core.data.location;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.util.List;

/**
 * BasicLocation represents a location with essential attributes such as name, world, types, warp, and position.
 * This is the base class for location related information shared between server and client.
 */
@Data
public class BasicLocation implements Serializable {

    /** The name of the location **/
    @SerializedName("name")
    protected String name;

    /** The world or dimension the location is in **/
    @SerializedName("world")
    protected String world;

    /** The types or categories of the location (e.g., city, landmark) **/
    @SerializedName("type")
    protected List<String> types;

    /** The warp point associated with the location, if any **/
    @SerializedName("warp")
    protected String warp;

    /** The position of the location in the world, represented as a Vec3d (x, y, z) **/
    @SerializedName("position")
    protected Vec3d position;

    /** Whether this location has project info or not */
    @SerializedName("pathfinder")
    protected String pathfinder;

    /** The location's unique identifier */
    @SerializedName("id")
    protected String id;

    /**
     * Custom serialization to handle transient Vec3d position
     *
     * @param stream The output stream
     * @throws IOException on I/O error
     */
    @Serial
    private void writeObject(ObjectOutputStream stream) throws IOException {

        stream.writeObject(id);
        stream.writeObject(name);
        stream.writeObject(world);
        stream.writeObject(types);
        stream.writeObject(warp);
        stream.writeObject(pathfinder);

        if (position != null) {
            stream.writeDouble(position.x);
            stream.writeDouble(position.y);
            stream.writeDouble(position.z);
        } else {
            stream.writeDouble(Double.NaN);
            stream.writeDouble(Double.NaN);
            stream.writeDouble(Double.NaN);
        }
    }

    /**
     * Custom deserialization to handle transient Vec3d position
     *
     * @param stream The input stream
     * @throws IOException            on I/O error
     * @throws ClassNotFoundException on class not found
     */
    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {

        id = (String) stream.readObject();
        name = (String) stream.readObject();
        world = (String) stream.readObject();
        types = (List<String>) stream.readObject();
        warp = (String) stream.readObject();
        pathfinder = (String) stream.readObject();

        double x = stream.readDouble();
        double y = stream.readDouble();
        double z = stream.readDouble();

        if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z))
            position = new Vec3d(x, y, z);
    }
}