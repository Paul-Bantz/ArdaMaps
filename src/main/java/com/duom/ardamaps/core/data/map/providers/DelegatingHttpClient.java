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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public {@link HttpClient} wrapper whose concrete class exposes {@link #shutdownNow()} to tileverse reflection.
 * Delegates all HTTP operations to an internal HttpClient instance.
 */
public final class DelegatingHttpClient extends HttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** Default per-request timeout applied when a request doesn't already specify one. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient delegate;

    /**
     * Constructs a DelegatingHttpClient wrapping the given HttpClient.
     *
     * @param delegate The underlying HttpClient to delegate to.
     */
    DelegatingHttpClient(HttpClient delegate) {

        this.delegate = delegate;
    }

    /**
     * Creates a new DelegatingHttpClient with default configuration.
     *
     * @return A new DelegatingHttpClient instance.
     */
    public static DelegatingHttpClient create() {

        return new DelegatingHttpClient(HttpClient.newBuilder()
                .sslContext(defaultSslContext())
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(Executors.newCachedThreadPool(daemonThreadFactory()))
                .build());
    }

    /**
     * Get the default SSL context.
     *
     * @return The default SSL context.
     * @throws IllegalStateException If the default SSL algorithm is not available.
     */
    private static SSLContext defaultSslContext() {

        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create default SSL context", e);
        }
    }

    /**
     * Create a thread factory that produces daemon threads.
     *
     * @return A thread factory for daemon threads.
     */
    private static ThreadFactory daemonThreadFactory() {

        AtomicInteger threadId = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("ardamaps-pmtiles-http-%02d".formatted(threadId.incrementAndGet()));
            return thread;
        };
    }

    /**
     * Get the optional cookie handler.
     *
     * @return The cookie handler, if configured.
     */
    @Override
    public Optional<CookieHandler> cookieHandler() {

        return delegate.cookieHandler();
    }

    /**
     * Get the optional connection timeout duration.
     *
     * @return The connection timeout, if configured.
     */
    @Override
    public Optional<Duration> connectTimeout() {

        return delegate.connectTimeout();
    }

    /**
     * Get the redirect policy.
     *
     * @return The redirect policy.
     */
    @Override
    public Redirect followRedirects() {

        return delegate.followRedirects();
    }

    /**
     * Get the optional proxy selector.
     *
     * @return The proxy selector, if configured.
     */
    @Override
    public Optional<ProxySelector> proxy() {

        return delegate.proxy();
    }

    /**
     * Get the SSL context.
     *
     * @return The SSL context.
     */
    @Override
    public SSLContext sslContext() {

        return delegate.sslContext();
    }

    /**
     * Get the SSL parameters.
     *
     * @return The SSL parameters.
     */
    @Override
    public SSLParameters sslParameters() {

        return delegate.sslParameters();
    }

    /**
     * Get the optional authenticator.
     *
     * @return The authenticator, if configured.
     */
    @Override
    public Optional<Authenticator> authenticator() {

        return delegate.authenticator();
    }

    /**
     * Get the HTTP protocol version.
     *
     * @return The HTTP protocol version.
     */
    @Override
    public Version version() {

        return delegate.version();
    }

    /**
     * Get the optional executor for asynchronous tasks.
     *
     * @return The executor, if configured.
     */
    @Override
    public Optional<Executor> executor() {

        return delegate.executor();
    }

    /**
     * Send an HTTP request synchronously.
     *
     * @param request             The HTTP request to send.
     * @param responseBodyHandler Handler for the response body.
     * @param <T>                 The response body type.
     * @return The HTTP response.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the operation is interrupted.
     */
    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {

        return delegate.send(withRequestTimeout(request), responseBodyHandler);
    }

    /**
     * Send an HTTP request asynchronously.
     *
     * @param request             The HTTP request to send.
     * @param responseBodyHandler Handler for the response body.
     * @param <T>                 The response body type.
     * @return A CompletableFuture that completes with the HTTP response.
     */
    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler
    ) {

        return delegate.sendAsync(withRequestTimeout(request), responseBodyHandler);
    }

    /**
     * Send an HTTP request asynchronously with push promise support.
     *
     * @param request             The HTTP request to send.
     * @param responseBodyHandler Handler for the response body.
     * @param pushPromiseHandler  Handler for push promise frames.
     * @param <T>                 The response body type.
     * @return A CompletableFuture that completes with the HTTP response.
     */
    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {

        return delegate.sendAsync(withRequestTimeout(request), responseBodyHandler, pushPromiseHandler);
    }

    /**
     * Returns the request unchanged if it already specifies a timeout, otherwise rebuilds it with
     * the default {@link #REQUEST_TIMEOUT} so a stalled server can never block a caller indefinitely.
     *
     * @param request The request to check.
     * @return The request, with a default timeout applied if none was set.
     */
    private static HttpRequest withRequestTimeout(HttpRequest request) {

        if (request.timeout().isPresent()) return request;

        return HttpRequest.newBuilder(request, (_, _) -> true)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }

    /**
     * Create a new WebSocket builder.
     *
     * @return A WebSocket builder.
     */
    @Override
    public WebSocket.Builder newWebSocketBuilder() {

        return delegate.newWebSocketBuilder();
    }

    /**
     * Initiate an orderly shutdown.
     */
    @Override
    public void shutdown() {

        delegate.shutdown();
    }

    /**
     * Force an immediate shutdown.
     */
    @Override
    public void shutdownNow() {

        delegate.shutdownNow();
    }

    /**
     * Wait for termination within the given duration.
     *
     * @param duration The maximum time to wait.
     * @return true if the client terminated, false if the timeout elapsed.
     * @throws InterruptedException If the wait is interrupted.
     */
    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {

        return delegate.awaitTermination(duration);
    }

    /**
     * Check if the client has terminated.
     *
     * @return true if the client is terminated, false otherwise.
     */
    @Override
    public boolean isTerminated() {

        return delegate.isTerminated();
    }

    /**
     * Close this client and shut it down.
     */
    @Override
    public void close() {

        delegate.shutdownNow();
    }
}
