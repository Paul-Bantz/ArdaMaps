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

import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
