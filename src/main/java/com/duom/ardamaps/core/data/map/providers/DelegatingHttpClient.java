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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Public {@link HttpClient} wrapper whose concrete class exposes {@link #shutdownNow()} to tileverse reflection.
 */
public final class DelegatingHttpClient extends HttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient delegate;

    DelegatingHttpClient(HttpClient delegate) {

        this.delegate = delegate;
    }

    public static DelegatingHttpClient create() {

        return new DelegatingHttpClient(HttpClient.newBuilder()
                .sslContext(defaultSslContext())
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(Executors.newCachedThreadPool(daemonThreadFactory()))
                .build());
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {

        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {

        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {

        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {

        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {

        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {

        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {

        return delegate.authenticator();
    }

    @Override
    public Version version() {

        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {

        return delegate.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {

        return delegate.send(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler
    ) {

        return delegate.sendAsync(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {

        return delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {

        return delegate.newWebSocketBuilder();
    }

    @Override
    public void shutdown() {

        delegate.shutdown();
    }

    @Override
    public void shutdownNow() {

        delegate.shutdownNow();
    }

    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {

        return delegate.awaitTermination(duration);
    }

    @Override
    public boolean isTerminated() {

        return delegate.isTerminated();
    }

    @Override
    public void close() {

        delegate.shutdownNow();
    }

    private static SSLContext defaultSslContext() {

        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create default SSL context", e);
        }
    }

    private static ThreadFactory daemonThreadFactory() {

        AtomicInteger threadId = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("ardamaps-pmtiles-http-%02d".formatted(threadId.incrementAndGet()));
            return thread;
        };
    }
}
