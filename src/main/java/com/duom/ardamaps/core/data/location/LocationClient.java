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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Data model representing a location within ArdaCraft's World for client-side use
 * This is a stripped version of Location
 */
public class LocationClient extends BasicLocation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Marker colour - default if unbound.
     * Transient - this field is set at runtime based on the representing Marker's colour, and is not serialized.
     */
    @Setter
    private transient int color = 0XFFAE7D53;

    /**
     * Marker highlight colour - default if unbound
     * Transient - this field is set at runtime based on the representing Marker's highlight colour, and is not serialized.
     */
    @Setter
    private transient int highlightColor = 0XFFAE7D53;

    /**
     * Marker icon - default if unbound
     * Transient - this field is set at runtime based on the representing Marker's icon, and is not serialized.
     */
    @Setter
    private transient Identifier icon = ModConstants.LANDMARK_ICON;

    /**
     * Whether the location has been visited by the player. This field is hydrated when client progress is loaded.
     * {@link com.duom.ardamaps.core.data.config.client.ClientProgress} is the source of truth for this field
     */
    private transient boolean visited;

    /**
     * The current exploration state for this location. This field is hydrated when client progress is loaded. */
    @Setter
    @Getter
    private transient ExplorationState explorationState;

    /** Default constructor */
    public int getColor() {

        return isRevealed() ? color : MarkersManager.get().unknownType().color();
    }

    /**
     * @return The identifier for the marker icon texture.  Depending on the exploration state of the location
     */
    public Identifier getIcon() {
        return (isRevealed() || isVisited()) ? icon : MarkersManager.get().unknownType().icon();
    }

    /**
     * @return Whether this location has been explored by the player. Depending on the exploration state of the location
     */
    public boolean isRevealed() {

        if (Objects.equals(position, Vec3d.ZERO)) {

            return ArdaMapsClient.CONFIG.isMapRevealAll() || isVisited();
        }

        return ArdaMapsClient.CONFIG.isMapRevealAll() || explorationState.ordinal() > ExplorationState.VISIBLE.ordinal();
    }

    /**
     * @return Whether the location is visible - i.e., the area's fog of war is partially uncovered
     */
    public boolean isVisible(){

        return ArdaMapsClient.CONFIG.isMapRevealAll() || explorationState != ExplorationState.HIDDEN;
    }

    /**
     * @return the name of the location or a translatable placeholder for the given location depending on the
     *  exploration state of the location
     */
    @Override
    public String getName() {

        if (isVisited() || ArdaMapsClient.CONFIG.isMapRevealAll()) return super.getName();

        return explorationState.ordinal() > ExplorationState.VISIBLE.ordinal() ?
                super.getName() : Text.translatable("ardamaps.location.unknown").getString();
    }

    /**
     * Updates the exploration status of this location. Only if its current state is lower than the new state.
     * Progress is persisted in {@link com.duom.ardamaps.core.data.config.client.ClientProgress} as such progress is
     * updated in parallel.
     *
     * @param state The exploration state to set for this location
     */
    public void updateExplorationState(ExplorationState state) {

        if (explorationState != null && state.ordinal() > explorationState.ordinal())
            this.explorationState = state;
        else if (explorationState ==  null)
            this.explorationState = state;
    }

    /**
     * @return the highlight colour
     */
    public int getHighlightColor() {
        return isRevealed() ? highlightColor : MarkersManager.get().unknownType().highlightColor();
    }

    /**
     * @return true if the player has visited this location, false otherwise. This is a dynamic property and is not persisted.
     */
    public boolean isVisited() {
        return ArdaMapsClient.CONFIG.isMapRevealAll() || visited;
    }

    /**
     * Sets the visited status of this location.
     * <p>
     * Progress is persisted in {@link com.duom.ardamaps.core.data.config.client.ClientProgress} as such progress is
     * updated in parallel
     *
     * @param visited Whether the player has visited this location.
     */
    public void setVisited(boolean visited) {
        this.visited = visited;

        if (visited)
            ArdaMapsClient.CONFIG.getClientProgress().getVisitedLocationIds().add(id);
        else
            ArdaMapsClient.CONFIG.getClientProgress().getVisitedLocationIds().remove(id);
    }

    /**
     * Synchronize the current exploration (state and visited flag) for this Location. This method is only supposed
     * to be called when client configuration s loaded and {@link com.duom.ardamaps.core.data.config.client.ClientProgress}
     * has been set.
     * It bypasses the client progress update in the setters.
     *
     * @param state   the exploration state for this location
     * @param visited  whether this location has been visited
     */
    public void synchronizeProgress(ExplorationState state, boolean visited) {

        this.explorationState = state;
        this.visited = visited;
    }

    /**
     * @return the location's ID and position
     */
    @Override
    public String toString() {
        return String.format("LocationClient{id=%s, pos=%s}", id, position);
    }
}