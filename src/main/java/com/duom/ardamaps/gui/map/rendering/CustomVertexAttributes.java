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

package com.duom.ardamaps.gui.map.rendering;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Method;

/**
 * Writes custom float vertex attributes to Minecraft's GUI {@link BufferBuilder}.
 */
final class CustomVertexAttributes {

    private static final Method BEGIN_ELEMENT = findBeginElement();

    private CustomVertexAttributes() {
    }

    static void set(VertexConsumer vertexConsumer, VertexFormatElement element, float... values) {

        if (!(vertexConsumer instanceof BufferBuilder bufferBuilder)) return;
        if (values.length != element.count()) {
            throw new IllegalArgumentException("Expected " + element.count() + " values for " + element);
        }

        try {
            long pointer = (long) BEGIN_ELEMENT.invoke(bufferBuilder, element);
            if (pointer == -1L) return;

            for (int i = 0; i < values.length; i++)
                MemoryUtil.memPutFloat(pointer + (long) i * Float.BYTES, values[i]);

        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to write custom vertex attribute " + element, e);
        }
    }

    private static Method findBeginElement() {

        try {
            Method method = BufferBuilder.class.getDeclaredMethod("beginElement", VertexFormatElement.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access BufferBuilder custom vertex attribute writer", e);
        }
    }
}
