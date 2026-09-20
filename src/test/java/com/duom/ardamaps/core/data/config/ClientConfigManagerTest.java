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

package com.duom.ardamaps.core.data.config;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.config.client.ProgressWipe;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for client exploration progress persistence and wipe handling.
 */
class ClientConfigManagerTest {

    /** Temporary configuration directory for each test. */
    @TempDir
    private Path tempDir;

    /** Mocked image construction used to isolate texture-backed exploration setup from native resources. */
    private MockedConstruction<NativeImage> mockedNativeImage;

    /** Mocked texture construction used to isolate dynamic texture registration from the Minecraft runtime. */
    private MockedConstruction<DynamicTexture> mockedDynamicTexture;

    /** Mocked static accessor for {@link Minecraft} so tests can provide a fake texture manager. */
    private MockedStatic<Minecraft> mockedMinecraftClient;

    /**
     * Installs the minimal mocked Minecraft client environment required for exploration texture creation.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    void setUp() {

        mockedNativeImage = Mockito.mockConstruction(NativeImage.class);
        mockedDynamicTexture = Mockito.mockConstruction(DynamicTexture.class);

        Minecraft mockClient = Mockito.mock(Minecraft.class);
        TextureManager mockTextureManager = Mockito.mock(TextureManager.class);
        Mockito.when(mockClient.getTextureManager()).thenReturn(mockTextureManager);
        mockedMinecraftClient = Mockito.mockStatic(Minecraft.class);
        mockedMinecraftClient.when(Minecraft::getInstance).thenReturn(mockClient);
    }

    /**
     * Releases mocked native-resource wrappers and restores the shared test config state.
     *
     * @throws Exception If the IO executor does not drain.
     */
    @AfterEach
    void tearDown() throws Exception {

        drainIoExecutor();
        mockedNativeImage.close();
        mockedDynamicTexture.close();
        mockedMinecraftClient.close();
        ArdaMapsClient.CONFIG = null;
        ArdaMapsClient.CONFIG_MANAGER = null;
    }

    /**
     * Verifies that saved progress can be restored after session-only state is cleared.
     */
    @Test
    void reloadClientProgress_restoresSavedProgressAfterSessionClear() {

        ClientConfigManager manager = createManager();
        seedProgress(manager);

        manager.saveProgressNow();
        manager.getConfig().getClientProgress().clearSessionState();

        assertTrue(manager.getConfig().getClientProgress().getExplorationState().isEmpty());
        assertTrue(manager.reloadClientProgress());

        var reloadedProgress = manager.getConfig().getClientProgress();
        assertTrue(reloadedProgress.getExplorationState().containsKey("test:dimension"));
        assertTrue(reloadedProgress.getVisitedLocationIds().contains("visited-location"));
        assertEquals(ExplorationState.REVEALED, reloadedProgress.getExplorationState("test:dimension", false).stateAt(0, 0));
    }

    /**
     * Verifies that a full reset writes a timestamped backup before saving empty progress.
     *
     * @throws Exception when test file operations fail.
     */
    @Test
    void wipeClientProgress_fullResetBacksUpPreviousProgressAndSavesEmptyFile() throws Exception {

        ClientConfigManager manager = createManager();
        seedProgress(manager);
        manager.saveProgressNow();

        manager.getConfig().setDimensions(List.of());
        manager.wipeClientProgress(ProgressWipe.FULL_RESET);
        manager.saveProgressNow();

        List<Path> backups = progressBackups();
        assertEquals(1, backups.size());
        assertTrue(Files.readString(backups.get(0)).contains("test:dimension"));
        assertFalse(Files.readString(progressFile()).contains("test:dimension"));
    }

    /**
     * Verifies that only the three most recent backup files are retained.
     *
     * @throws Exception when test file operations fail.
     */
    @Test
    void wipeClientProgress_retainsOnlyThreeNewestBackups() throws Exception {

        ClientConfigManager manager = createManager();
        seedProgress(manager);
        manager.saveProgressNow();
        manager.getConfig().setDimensions(List.of());

        for (int idx = 0; idx < 4; idx++) {
            manager.wipeClientProgress(ProgressWipe.FULL_RESET);
            manager.saveProgressNow();

            Path newest = progressBackups().stream()
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow();
            Files.setLastModifiedTime(newest, FileTime.fromMillis(idx + 1L));
        }

        List<Path> backups = progressBackups();
        assertEquals(3, backups.size());
        assertEquals(List.of(2L, 3L, 4L), backups.stream()
                .map(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                })
                .sorted()
                .toList());
    }

    /**
     * Verifies that corrupt progress is backed up and replaced without failing manager construction.
     *
     * @throws Exception when test file operations fail.
     */
    @Test
    void constructor_corruptProgressBacksUpAndInstallsEmptyProgress() throws Exception {

        Files.createDirectories(tempDir);
        Files.writeString(progressFile(), "{not-valid-json");

        ClientConfigManager manager = createManager();
        manager.saveProgressNow();

        assertTrue(manager.getConfig().getClientProgress().getExplorationState().isEmpty());
        assertEquals(1, progressBackups().size());
        assertTrue(Files.readString(progressBackups().get(0)).contains("{not-valid-json"));
    }

    /**
     * Verifies that a ranged migration with no stale flat entry does not create a backup.
     *
     * @throws Exception when test file operations fail.
     */
    @Test
    void wipeClientProgress_rangedMigrationNoOpCreatesNoBackup() throws Exception {

        ClientConfigManager manager = createManager();
        manager.getConfig().setDimensions(List.of(rangedDimension()));
        manager.getConfig().getClientProgress().getExplorationState("test:dimension", 0, true);
        manager.saveProgressNow();

        manager.wipeClientProgress(ProgressWipe.RANGED_MIGRATION, List.of(rangedDimension()));
        manager.saveProgressNow();

        assertTrue(progressBackups().isEmpty());
    }

    /**
     * Creates a client config manager rooted in the test temporary directory.
     *
     * @return A manager whose config is installed into {@link ArdaMapsClient}.
     */
    private ClientConfigManager createManager() {

        ClientConfigManager manager = new ClientConfigManager(
                tempDir.resolve("config.json").toString(),
                tempDir.resolve("locations.json").toString(),
                tempDir.resolve("regions.json").toString(),
                progressFile().toString());
        ArdaMapsClient.CONFIG_MANAGER = manager;
        ArdaMapsClient.CONFIG = manager.getConfig();
        return manager;
    }

    /**
     * Adds one revealed cell and one visited location to the manager progress.
     *
     * @param manager The manager to seed.
     */
    private void seedProgress(ClientConfigManager manager) {

        manager.getConfig().setDimensions(List.of(flatDimension()));
        var progress = manager.getConfig().getClientProgress();
        var exploration = progress.getExplorationState("test:dimension", true);
        exploration.markCell(0, 0, ExplorationState.REVEALED);
        progress.getVisitedLocationIds().add("visited-location");
    }

    /**
     * Returns the path to the test progress file.
     *
     * @return Test progress file path.
     */
    private Path progressFile() {

        return tempDir.resolve("progress.json");
    }

    /**
     * Lists progress backup files in deterministic filename order.
     *
     * @return Progress backup files.
     * @throws IOException when listing fails.
     */
    private List<Path> progressBackups() throws IOException {

        try (var paths = Files.list(tempDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith("progress.json.backup-"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Builds a small non-ranged dimension definition for progress creation.
     *
     * @return A dimension definition.
     */
    private static Dimension flatDimension() {

        return new Dimension("Test", "test:dimension", 1f, 0, 15, 0, 15, false);
    }

    /**
     * Builds a small ranged dimension definition for migration tests.
     *
     * @return A ranged dimension definition.
     */
    private static Dimension rangedDimension() {

        Dimension dimension = flatDimension();
        dimension.getMapLayers().add(new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png",
                List.of(new MapLayerRange(0, "low.pmtiles", -64, 0))));
        return dimension;
    }

    /**
     * Waits for all queued IO executor tasks to complete.
     *
     * @throws Exception If the IO executor does not drain.
     */
    private static void drainIoExecutor() throws Exception {

        CompletableFuture.runAsync(() -> {
        }, ArdaMaps.IO_EXECUTOR).get(2, TimeUnit.SECONDS);
    }
}
