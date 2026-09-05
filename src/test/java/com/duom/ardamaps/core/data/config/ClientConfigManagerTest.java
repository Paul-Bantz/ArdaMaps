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
import com.duom.ardamaps.core.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for client configuration progress handling.
 */
class ClientConfigManagerTest {

    /** Temporary configuration directory. */
    @TempDir
    private Path tempDir;

    /**
     * Waits for async configuration saves before JUnit deletes the temporary directory.
     *
     * @throws Exception If the IO executor does not drain.
     */
    @AfterEach
    void waitForConfigSaves() throws Exception {

        drainIoExecutor();
        ArdaMapsClient.CONFIG = null;
    }

    /**
     * Verifies that reset progress removes offline progress from memory and disk.
     *
     * @throws Exception If test file setup or async reset fails.
     */
    @Test
    void resetProgress_whenOffline_clearsStateAndDeletesProgressFile() throws Exception {

        Files.createDirectories(configDir());
        Files.writeString(progressPath(), """
                {
                  "visitedLocationIds": [
                    "spawn"
                  ]
                }
                """);

        try (var client = Mockito.mockStatic(Client.class)) {
            client.when(Client::world).thenReturn(null);

            ClientConfigManager manager = manager();
            ArdaMapsClient.CONFIG = manager.getConfig();
            manager.getConfig().getClientProgress().getExplorationState("minecraft:overworld", true);

            manager.resetProgress();
            drainIoExecutor();

            assertFalse(Files.exists(progressPath()));
            assertTrue(manager.getConfig().getClientProgress().getExplorationState().isEmpty());
            assertTrue(manager.getConfig().getClientProgress().getVisitedLocationIds().isEmpty());
        }
    }

    /**
     * Creates a client config manager rooted in the temporary directory.
     *
     * @return A client config manager.
     */
    private ClientConfigManager manager() {

        return new ClientConfigManager(
                configDir().resolve("client.json").toString(),
                configDir().resolve("client-locations.json").toString(),
                configDir().resolve("region-texture-lookup.json").toString(),
                progressPath().toString());
    }

    /**
     * Gets the test client progress path.
     *
     * @return The test client progress path.
     */
    private Path progressPath() {

        return configDir().resolve("progress.json");
    }

    /**
     * Gets the test ArdaMaps config directory.
     *
     * @return The test ArdaMaps config directory.
     */
    private Path configDir() {

        return tempDir.resolve("arda-maps");
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
