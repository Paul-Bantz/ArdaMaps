#version 150

/*
 * This file is part of ArdaMaps.
 * Portions of this file are derived from BlueMap, licensed under the MIT License (MIT).

 * Original work:
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors

 * Modifications and integration:
 * Copyright (c) 2026 Duom / ArdaMaps

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the 'Software'), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
uniform sampler2D TileTex;
uniform float SunlightStrength;
uniform float AmbientLight;
uniform float LodScale;
uniform vec2 TexelSize; // (1/imageSize, 1/(imageSize*2)) — one texel step in UV space

in vec2 texCoord;
out vec4 fragColor;

// Decodes a signed world height from BlueMap's meta encoding.
// Green channel carries the high byte (× 256), blue carries the low byte.
// Values >= 32768 are negative (two's-complement in 16-bit unsigned space).
float metaToHeight(vec4 meta) {
    float h = meta.g * 65280.0 + meta.b * 255.0;
    if (h >= 32768.0) {
        return -(65535.0 - h);
    }
    return h;
}

void main() {
    // --- Color (top half of the PNG: v in [0, ~0.5]) ---
    vec4 color = texture(TileTex, texCoord);

    // --- Meta (bottom half of the PNG: v in [~0.5, 1.0]) ---
    // Adding 0.5 to V shifts from the colour region to the shading-data region.
    vec2 metaBase = vec2(texCoord.x, texCoord.y + 0.5);
    vec4 meta  = texture(TileTex, metaBase);
    vec4 metaX = texture(TileTex, vec2(texCoord.x + TexelSize.x, texCoord.y + 0.5));
    vec4 metaZ = texture(TileTex, vec2(texCoord.x, texCoord.y + TexelSize.y + 0.5));

    float height  = metaToHeight(meta);
    float heightX = metaToHeight(metaX);
    float heightZ = metaToHeight(metaZ);

    // Height gradient in X and Z directions, normalised by LOD scale so that
    // coarser tiles (where one texel covers more world blocks) produce the same
    // visual shading intensity as fine tiles.
    float heightDiff = ((height - heightX) + (height - heightZ)) / LodScale;
    float shade = clamp(heightDiff * 0.06, -0.2, 0.04);

    color.rgb += shade;

    // Block-light stored in the red channel (0–255 -> 0–15 light levels).
    float blockLight = meta.r * 255.0;
    float light = mix(blockLight, 15.0, SunlightStrength);
    color.rgb *= mix(AmbientLight, 1.0, light / 15.0);

    fragColor = color;
}
