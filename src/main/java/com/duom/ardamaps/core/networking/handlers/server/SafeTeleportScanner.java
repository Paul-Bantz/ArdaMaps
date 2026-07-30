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

package com.duom.ardamaps.core.networking.handlers.server;

import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves map teleport requests against vanilla collision shapes instead of whole-block solidity flags.
 */
public final class SafeTeleportScanner {

    /**
     * Hidden constructor for this stateless utility class.
     */
    private SafeTeleportScanner() {
    }

    /**
     * Returns the exact Y coordinate a player can stand at for an integer feet block candidate.
     * <p>
     * The result uses {@link net.minecraft.world.level.BlockGetter#getBlockFloorHeight(BlockPos)} for shape-aware standing
     * height, {@link DismountHelper#canDismountTo(net.minecraft.world.level.CollisionGetter, Vec3,
     * net.minecraft.world.entity.LivingEntity, Pose)} for the real standing bounding box check, and an explicit
     * fluid/fire hazard pass because those hazards can have empty collision shapes.
     *
     * @param world The destination world to inspect for collision, fluid, and fire hazards.
     * @param player The player whose standing dimensions are being placed.
     * @param x The snapped X coordinate for the destination column.
     * @param feetY The integer feet block candidate being evaluated.
     * @param z The snapped Z coordinate for the destination column.
     * @return The fractional standing Y coordinate, or empty when the candidate cannot safely hold the player.
     */
    public static OptionalDouble standingHeightAt(ServerLevel world, ServerPlayer player,
                                                  double x, int feetY, double z) {

        BlockPos feet = BlockPos.containing(x, feetY, z);
        OptionalDouble standY = resolveStandY(feetY, world.getBlockFloorHeight(feet),
                world.getFluidState(feet).is(FluidTags.WATER));

        if (standY.isEmpty()) return OptionalDouble.empty();

        double y = standY.getAsDouble();
        if (!DismountHelper.canDismountTo(world, new Vec3(x, y, z), player, Pose.STANDING)) {
            return OptionalDouble.empty();
        }

        if (world.getFluidState(eyeBlock(player, x, y, z)).is(FluidTags.WATER)) {
            return OptionalDouble.empty();
        }

        if (containsHazard(world, player.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z))) {
            return OptionalDouble.empty();
        }

        return standY;
    }

    /**
     * Resolves the standing Y from the vanilla dismount height for a candidate feet block.
     * <p>
     * This intentionally inlines {@link DismountHelper#isBlockFloorValid(double)} as
     * {@code !Double.isInfinite(height) && height < 1.0} so unit tests can exercise the pure logic without a
     * Minecraft bootstrap.
     *
     * @param feetY The integer candidate feet Y coordinate.
     * @param dismountHeight The shape-derived standing height relative to the candidate block.
     * @param feetWater Whether the candidate feet block contains water.
     * @return The exact standing Y coordinate, or empty when unsupported and not swimmable.
     */
    static OptionalDouble resolveStandY(int feetY, double dismountHeight, boolean feetWater) {

        if (!Double.isInfinite(dismountHeight) && dismountHeight < 1.0D) {
            return OptionalDouble.of(feetY + dismountHeight);
        }

        if (feetWater) return OptionalDouble.of(feetY);

        return OptionalDouble.empty();
    }

    /**
     * Snaps a horizontal coordinate to the center of its block column.
     *
     * @param coord The source coordinate, which may already be centered or may be negative.
     * @return The center coordinate of the block containing {@code coord}.
     */
    static double blockCenter(double coord) {

        return Math.floor(coord) + 0.5D;
    }

    /**
     * Calculates the block containing the player's standing eye position.
     *
     * @param player The player whose eye height should be used.
     * @param x The destination X coordinate.
     * @param standY The resolved standing Y coordinate.
     * @param z The destination Z coordinate.
     * @return The block position containing the player's eyes.
     */
    private static BlockPos eyeBlock(ServerPlayer player, double x, double standY, double z) {

        return BlockPos.containing(x, standY + player.getEyeHeight(Pose.STANDING), z);
    }

    /**
     * Checks every block touched by the player's standing box for non-collision hazards.
     *
     * @param world The destination world containing the candidate blocks.
     * @param playerBox The player's standing bounding box at the candidate destination.
     * @return True if lava or fire intersects the standing box, false otherwise.
     */
    private static boolean containsHazard(ServerLevel world, AABB playerBox) {

        return BlockPos.betweenClosedStream(playerBox).anyMatch(pos ->
                world.getFluidState(pos).is(FluidTags.LAVA) || world.getBlockState(pos).is(BlockTags.FIRE));
    }
}
