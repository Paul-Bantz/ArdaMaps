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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DelegatingHttpClient}.
 */
class DelegatingHttpClientTest {

    /**
     * Verifies tileverse's reflective shutdown path can invoke {@code shutdownNow()} on the wrapper class.
     */
    @Test
    void reflectedShutdownNow_invokesDelegate() throws NoSuchMethodException {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);
        var shutdownNow = client.getClass().getMethod("shutdownNow");

        assertDoesNotThrow(() -> shutdownNow.invoke(client));

        verify(delegate).shutdownNow();
    }

    /**
     * Verifies close uses immediate shutdown and never delegates to HttpClient.close().
     */
    @Test
    void close_invokesShutdownNow() {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);

        client.close();

        verify(delegate).shutdownNow();
    }

    /**
     * Verifies selected accessors are transparent pass-throughs to the wrapped client.
     */
    @Test
    void accessors_delegateToWrappedClient() throws Exception {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);
        CookieHandler cookieHandler = mock(CookieHandler.class);
        ProxySelector proxySelector = mock(ProxySelector.class);
        SSLContext sslContext = SSLContext.getDefault();
        SSLParameters sslParameters = new SSLParameters();
        Executor executor = Runnable::run;

        when(delegate.cookieHandler()).thenReturn(Optional.of(cookieHandler));
        when(delegate.connectTimeout()).thenReturn(Optional.of(Duration.ofSeconds(7)));
        when(delegate.followRedirects()).thenReturn(HttpClient.Redirect.NORMAL);
        when(delegate.proxy()).thenReturn(Optional.of(proxySelector));
        when(delegate.sslContext()).thenReturn(sslContext);
        when(delegate.sslParameters()).thenReturn(sslParameters);
        when(delegate.version()).thenReturn(HttpClient.Version.HTTP_2);
        when(delegate.executor()).thenReturn(Optional.of(executor));

        assertEquals(Optional.of(cookieHandler), client.cookieHandler());
        assertEquals(Optional.of(Duration.ofSeconds(7)), client.connectTimeout());
        assertEquals(HttpClient.Redirect.NORMAL, client.followRedirects());
        assertEquals(Optional.of(proxySelector), client.proxy());
        assertSame(sslContext, client.sslContext());
        assertSame(sslParameters, client.sslParameters());
        assertEquals(HttpClient.Version.HTTP_2, client.version());
        assertEquals(Optional.of(executor), client.executor());
    }

    /**
     * Verifies {@link DelegatingHttpClient#create()} supplies tileverse with a client that has a timeout.
     */
    @Test
    void create_setsExplicitConnectTimeout() {

        try (DelegatingHttpClient client = DelegatingHttpClient.create()) {
            assertEquals(Optional.of(Duration.ofSeconds(30)), client.connectTimeout());
        }
    }

    /**
     * Verifies {@code send} applies the default request timeout when the caller's request has none,
     * so a stalled server can never block a synchronous range read indefinitely.
     */
    @SuppressWarnings("unchecked")
    @Test
    void send_requestWithoutTimeout_delegatesWithDefaultTimeout() throws Exception {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.test/tile")).build();
        HttpResponse<Void> response = mock(HttpResponse.class);

        doReturn(response).when(delegate).send(any(), any());

        client.send(request, HttpResponse.BodyHandlers.discarding());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).send(captor.capture(), any());
        assertEquals(Optional.of(Duration.ofSeconds(15)), captor.getValue().timeout());
        assertEquals(request.uri(), captor.getValue().uri());
    }

    /**
     * Verifies {@code send} leaves an explicit request timeout untouched.
     */
    @SuppressWarnings("unchecked")
    @Test
    void send_requestWithExplicitTimeout_isPassedThroughUnchanged() throws Exception {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.test/tile"))
                .timeout(Duration.ofSeconds(2))
                .build();
        HttpResponse<Void> response = mock(HttpResponse.class);

        doReturn(response).when(delegate).send(any(), any());

        client.send(request, HttpResponse.BodyHandlers.discarding());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).send(captor.capture(), any());
        assertEquals(Optional.of(Duration.ofSeconds(2)), captor.getValue().timeout());
    }

    /**
     * Verifies {@code sendAsync} also applies the default timeout when absent.
     */
    @SuppressWarnings("unchecked")
    @Test
    void sendAsync_requestWithoutTimeout_delegatesWithDefaultTimeout() {

        HttpClient delegate = mock(HttpClient.class);
        var client = new DelegatingHttpClient(delegate);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.test/tile")).build();
        HttpResponse<Void> response = mock(HttpResponse.class);

        doReturn(CompletableFuture.completedFuture(response)).when(delegate).sendAsync(any(), any());

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(delegate).sendAsync(captor.capture(), any());
        assertEquals(Optional.of(Duration.ofSeconds(15)), captor.getValue().timeout());
    }
}
