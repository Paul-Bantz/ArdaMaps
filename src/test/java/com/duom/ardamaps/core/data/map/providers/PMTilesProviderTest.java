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

package com.duom.ardamaps.core.data.map.providers;

import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import io.tileverse.pmtiles.PMTilesReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PMTilesProvider}'s tile-loading lifecycle: the reader/closed race that used to
 * surface as a repeating {@link NullPointerException}, and the transport-failure/missing-tile
 * bookkeeping that keeps a broken or sparse source from being retried every frame.
 */
class PMTilesProviderTest {

    /**
     * Verifies an {@link IOException} from the reader marks the key transport-failed rather than
     * leaving it immediately retriable.
     */
    @Test
    @Timeout(5)
    void loadTile_readerIOException_marksTransportFailure() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 1, 1);

        when(reader.getTile(anyLong())).thenThrow(new IOException("boom"));
        provider.reader = reader;

        provider.loadTile(key);

        awaitTrue(() -> !provider.loading.contains(key));

        assertTrue(provider.peek(key).isEmpty());

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertTrue(provider.peek(key).isEmpty(), "Transport-failed key must not load");
    }

    /**
     * Verifies an empty {@link Optional} tile (present in range but absent from the archive) marks
     * the key as missing so it is never retried, distinguishing it from a transport failure.
     */
    @Test
    @Timeout(5)
    void loadTile_emptyOptional_marksMissing() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 2, 2);

        when(reader.getTile(anyLong())).thenReturn(Optional.empty());
        provider.reader = reader;

        provider.loadTile(key);

        awaitTrue(() -> provider.missingKeys.getIfPresent(key) != null);
    }

    /**
     * Verifies {@code close()} while a load is queued does not throw and leaves the provider's
     * transient state clean, reproducing the conditions that used to surface as a repeating NPE.
     */
    @Test
    @Timeout(5)
    void close_whileLoadInFlight_doesNotThrowAndClearsLoadingState() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 3, 3);

        // Block the reader call so close() can race the in-flight task deterministically.
        var releaseLatch = new CountDownLatch(1);
        when(reader.getTile(anyLong())).thenAnswer(_ -> {
            releaseLatch.await();
            return Optional.empty();
        });
        provider.reader = reader;

        assertTrue(provider.loading.add(key));
        provider.loadTile(key);

        assertDoesNotThrow(provider::close);
        releaseLatch.countDown();

        awaitTrue(() -> !provider.loading.contains(key));
    }

    /**
     * Polls the given condition until it becomes true, failing the test after a bounded timeout
     * instead of hanging (the surrounding {@code @Timeout} is the hard backstop).
     *
     * @param condition The condition to poll.
     */
    @SuppressWarnings("BusyWait")
    private static void awaitTrue(BooleanSupplier condition) {

        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async condition");
            }
        }
        fail("Condition not met within timeout");
    }

    /**
     * Verifies a null reader (e.g. never configured, or already closed) is a no-op rather than an NPE.
     */
    @Test
    void loadTile_nullReader_clearsLoadingWithoutThrowing() {

        var provider = new TestPMTilesProvider();
        var key = new PmTileKey(4, 4, 4);

        provider.loading.add(key);

        assertDoesNotThrow(() -> provider.loadTile(key));

        assertFalse(provider.loading.contains(key));
    }

    /**
     * Minimal concrete {@link PMTilesProvider} for testing; PMTilesProvider itself is abstract only
     * to force construction through the file/HTTP {@code init(...)} factories.
     */
    private static final class TestPMTilesProvider extends PMTilesProvider {
    }
}
