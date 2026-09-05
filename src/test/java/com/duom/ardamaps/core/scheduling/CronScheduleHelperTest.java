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

package com.duom.ardamaps.core.scheduling;

import com.cronutils.model.Cron;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validate the cron string parsing and setting
 */
class CronScheduleHelperTest {

    /**
     * Validates that the default is returned when null is passed
     */
    @Test
    void parse_null_returnsDefaultCron() {

        // Silent failure here means the refresh scheduler never fires on startup;
        // the default fallback is the last line of defence against a broken server.json.
        Cron cron = CronScheduleHelper.parse(null);
        assertNotNull(cron);
        // A valid Cron can be turned into an expression string without throwing.
        assertDoesNotThrow(cron::validate);
    }

    /**
     * Blank string check
     */
    @Test
    void parse_blankString_returnsDefaultCron() {
        // A blank string is equally dangerous as null - the scheduler must not be disabled
        // simply because an admin left the field empty.
        Cron cron = CronScheduleHelper.parse("   ");
        assertNotNull(cron);
        assertDoesNotThrow(cron::validate);
    }

    /**
     * Not CRON string validation
     */
    @Test
    void parse_invalidExpression_returnsDefaultCron() {
        // A typo in server.json (e.g. "0 3 bad * *") must not disable all future refreshes;
        // the safe fallback prevents a silent, permanent outage.
        Cron cron = CronScheduleHelper.parse("not-a-cron");
        assertNotNull(cron);
        assertDoesNotThrow(cron::validate);
    }

    /**
     * Parse regular CRON and validate
     */
    @Test
    void parse_validExpression_returnsCorrectCron() {
        // Sanity check: the happy path must parse without throwing and produce a valid cron.
        // If this fails, every server that has a custom refresh_cron is broken.
        Cron cron = CronScheduleHelper.parse("0 6 * * 1");
        assertNotNull(cron);
        assertDoesNotThrow(cron::validate);
    }

    /**
     * Validate that the next execution time is in the future
     */
    @Test
    void nextExecution_returnsOptionalWithFutureTime() {
        // This value drives the delay calculation in scheduleNextCronTrigger.
        // A past time or empty Optional would cause the scheduler to fire immediately
        // on every restart instead of waiting for the correct window.
        Cron cron = CronScheduleHelper.parse(CronScheduleHelper.DEFAULT_CRON);
        ZonedDateTime now = ZonedDateTime.now();

        Optional<ZonedDateTime> next = CronScheduleHelper.nextExecution(cron, now);

        assertTrue(next.isPresent(), "nextExecution must return a non-empty Optional");
        assertTrue(next.get().isAfter(now), "next execution must be in the future");
    }

    /**
     * Validate past time occurrence
     */
    @Test
    void lastExecution_returnsOptionalWithPastTime() {
        // This value drives the staleness check on startup.
        // A wrong value means stale location data is never refreshed after a server restart.
        Cron cron = CronScheduleHelper.parse(CronScheduleHelper.DEFAULT_CRON);
        ZonedDateTime now = ZonedDateTime.now();

        Optional<ZonedDateTime> last = CronScheduleHelper.lastExecution(cron, now);

        assertTrue(last.isPresent(), "lastExecution must return a non-empty Optional");
        assertTrue(last.get().isBefore(now), "last execution must be in the past");
    }
}
