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

package com.duom.ardamaps.core.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * A custom item representing a Toposcope - a map-reading device used to display
 * the ArdaMaps overlay and map screen. Styled similarly to a book item but with
 * a custom amber/gold colour and name.
 */
public class GuideBookItem extends Item {

    /**
     * Constructs a new GuideBookItem with the specified settings.
     *
     * @param settings The item settings to apply to this item.
     */
    public GuideBookItem(Settings settings) {
        super(settings);
    }

    /**
     * Returns the display name of the item, styled with a custom amber/gold colour and no italics.
     *
     * @param stack The item stack for which to get the name.
     * @return The styled display name of the item.
     */
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("ardamaps.mod.item.name")
                .setStyle(Style.EMPTY.withColor(0xD4A017).withItalic(false));
    }
}

