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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Narrow bridge for submitting custom GUI render states through {@link GuiGraphicsExtractor}.
 */
final class GuiRenderStateAccess {

    private static final Field GUI_RENDER_STATE = findField("guiRenderState");

    private static final Field SCISSOR_STACK = findField("scissorStack");

    private static final Method SCISSOR_PEEK = findScissorPeek();

    private GuiRenderStateAccess() {
    }

    static void add(GuiGraphicsExtractor context, GuiElementRenderState element) {

        try {
            ((GuiRenderState) GUI_RENDER_STATE.get(context)).addGuiElement(element);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to add custom GUI render state", e);
        }
    }

    static ScreenRectangle scissorArea(GuiGraphicsExtractor context) {

        try {
            Object stack = SCISSOR_STACK.get(context);
            return (ScreenRectangle) SCISSOR_PEEK.invoke(stack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read current GUI scissor area", e);
        }
    }

    private static Field findField(String name) {

        try {
            Field field = GuiGraphicsExtractor.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access GuiGraphicsExtractor." + name, e);
        }
    }

    private static Method findScissorPeek() {

        try {
            Field field = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
            field.setAccessible(true);
            Method method = field.getType().getDeclaredMethod("peek");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access GuiGraphicsExtractor scissor peek", e);
        }
    }
}
