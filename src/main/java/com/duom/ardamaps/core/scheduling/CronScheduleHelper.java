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
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Utility class for parsing and evaluating Unix/Posix 5-field CRON expressions.
 * <p>
 * Any invalid or missing CRON expression falls back to the built-in default
 * ({@value #DEFAULT_CRON}) with a warning logged to the server log.
 */
public final class CronScheduleHelper {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(CronScheduleHelper.class);
    
    /** Default CRON schedule: every 4 days at 03:00 AM. */
    public static final String DEFAULT_CRON = "0 3 */4 * *";

    /**
     * Initialize the parser as a UNIX/POSIX cron parser
     */
    private static final CronParser PARSER = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    /** Private constructor to prevent instantiation of this utility class. */
    private CronScheduleHelper() {}

    /**
     * Parses the given CRON expression as a Unix/Posix 5-field expression.
     * <p>
     * If the expression is {@code null}, blank, or syntactically invalid, a warning is
     * logged and the default schedule ({@value #DEFAULT_CRON}) is returned instead.
     * This means existing server configs that have {@code "refresh_period_days"} but no
     * {@code "refresh_cron"} will automatically receive the default schedule.
     *
     * @param expression The CRON string to parse (may be {@code null}).
     * @return A valid, parsed {@link Cron} - always non-null.
     */
    public static Cron parse(String expression) {

        if (expression == null || expression.isBlank()) {
            LOGGER.warn(
                    "[ArdaMaps] refresh_cron is missing or blank in server.json. " +
                    "Falling back to default schedule: '{}'", DEFAULT_CRON);
            return parseDefault();
        }

        try {
            Cron cron = PARSER.parse(expression);
            cron.validate();
            return cron;
        } catch (IllegalArgumentException e) {
            LOGGER.warn(
                    "[ArdaMaps] refresh_cron '{}' is invalid ({}). " +
                    "Falling back to default schedule: '{}'",
                    expression, e.getMessage(), DEFAULT_CRON);
            return parseDefault();
        }
    }

    /**
     * Computes the next execution time for the given {@link Cron} after {@code from}.
     *
     * @param cron The CRON definition.
     * @param from The reference time (usually {@link ZonedDateTime#now()}).
     * @return The next execution time, or {@code Optional.empty()} if none can be computed.
     */
    public static Optional<ZonedDateTime> nextExecution(Cron cron, ZonedDateTime from) {
        return ExecutionTime.forCron(cron).nextExecution(from);
    }

    /**
     * Computes the most recent past execution time for the given {@link Cron} before {@code from}.
     * Used on startup to detect whether the data is stale relative to the CRON schedule.
     *
     * @param cron The CRON definition.
     * @param from The reference time (usually {@link ZonedDateTime#now()}).
     * @return The last execution time, or {@code Optional.empty()} if none can be computed.
     */
    public static Optional<ZonedDateTime> lastExecution(Cron cron, ZonedDateTime from) {
        return ExecutionTime.forCron(cron).lastExecution(from);
    }

    /**
     * @return the parsed default CRON schedule (every 4 days at 03:00 AM).
     */
    private static Cron parseDefault() {
        return PARSER.parse(DEFAULT_CRON);
    }
}

