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

package com.duom.ardamaps.core.data.conversion;

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.Vec2d;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Utility class for projecting 3D world positions to 2D screen coordinates
 * and calculating horizontal angles relative to the player's view.
 */
public class VectorProjection {

    /**
     * Projects a 3D world position to 2D screen coordinates.
     * Returns null if the point is behind the camera.
     */
    public static @Nullable Vec2f projectToScreen(Vec3d worldPos) {

        var client = Client.mc();
        Camera camera = client.gameRenderer.getCamera();

        // Calculate the position relative to the camera
        Vec3d cameraPosition = camera.getPos();
        Vec3d relativePosition = worldPos.subtract(cameraPosition);

        // Rotate the relative position to match the camera's view direction
        Quaternionf rot = new Quaternionf(camera.getRotation()).conjugate();
        Vector3f vec = new Vector3f(
                (float) relativePosition.x,
                (float) relativePosition.y,
                (float) relativePosition.z
        );
        vec.rotate(rot);

        // Check if the point is behind the camera - in which case end here
        if (vec.z <= 0.0f) return null;

        // Extract the coordinates in camera space
        float camX = -vec.x;
        float camY = vec.y;
        float camZ = vec.z;

        // Get screen dimensions and calculate aspect ratio
        int screenW = Client.getScaledWindowWidth();
        int screenH = Client.getScaledWindowHeight();
        float aspect = (float) screenW / screenH;

        // Calculate FoV
        float fovY = (float) Math.toRadians(client.options.getFov().getValue());
        float tanHalfFov = (float) Math.tan(fovY / 2.0f);

        /*
         * Convert to normalized device coordinates (NDC)
         * This maps the 3D position to a coordinate system where -1 to 1 represents the screen edges
         * We divide by distance (camZ) to create perspective - things farther away appear smaller
         */
        float ndcX = (camX / camZ) / (tanHalfFov * aspect);
        float ndcY = (camY / camZ) / tanHalfFov;

        // Check if the point is outside the screen boundaries
        if (ndcX < -1 || ndcX > 1 || ndcY < -1 || ndcY > 1) return null;

        // Convert from NDC (-1 to 1) to actual screen coordinates
        float screenX = (ndcX + 1.0f) * 0.5f * screenW;
        float screenY = (1.0f - (ndcY + 1.0f) * 0.5f) * screenH;

        return new Vec2f(screenX, screenY);
    }

    /**
     * Projects a 3D world position to a horizontal angle (compass bearing) relative to the player's view.
     * South is 0°, West is 90°, North is 180°, East is 270°.
     *
     * @param worldPos The 3D world position to project
     * @return The horizontal angle in degrees
     */
    public static float projectToHorizontalAngle(Vec3d worldPos) {

        return projectToHorizontalAngle(worldPos.x, worldPos.z);
    }

    /**
     * Projects a 2D world position (x, z) to a horizontal angle (compass bearing) relative to the player's view.
     * South is 0°, West is 90°, North is 180°, East is 270°.
     *
     * @param x The X coordinate in the world
     * @param z The Z coordinate in the world
     * @return The horizontal angle in degrees
     */
    public static float projectToHorizontalAngle(double x, double z) {

        Camera camera = Client.mc().gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();

        double dx = x - cameraPos.x;
        double dz = z - cameraPos.z;

        float angleDegrees = (float) Math.toDegrees(Math.atan2(dx, dz));

        return (180 - angleDegrees + 180) % 360;
    }

    /**
     * Projects a 2D world position (x, z) to a horizontal angle (compass bearing) relative to the player's view.
     * South is 0°, West is 90°, North is 180°, East is 270°.
     *
     * @param worldPos The 2D world position to project
     * @return The horizontal angle in degrees
     */
    public static float projectToHorizontalAngle(Vec2d worldPos) {

        return projectToHorizontalAngle(worldPos.x(), worldPos.y());
    }
}