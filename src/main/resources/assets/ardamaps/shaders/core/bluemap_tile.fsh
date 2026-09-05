#version 330

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

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in vec4 tileParams;

out vec4 fragColor;

float metaToHeight(vec4 meta) {
    float h = meta.g * 65280.0 + meta.b * 255.0;
    if (h >= 32768.0) {
        return -(65535.0 - h);
    }
    return h;
}

void main() {
    float sunlightStrength = tileParams.x;
    float ambientLight = tileParams.y;
    float lodScale = tileParams.z;
    vec2 texelSize = vec2(tileParams.w, tileParams.w * 0.5);

    vec4 color = texture(Sampler0, texCoord0);

    vec2 metaBase = vec2(texCoord0.x, texCoord0.y + 0.5);
    vec4 meta = texture(Sampler0, metaBase);
    vec4 metaX = texture(Sampler0, vec2(texCoord0.x + texelSize.x, texCoord0.y + 0.5));
    vec4 metaZ = texture(Sampler0, vec2(texCoord0.x, texCoord0.y + texelSize.y + 0.5));

    float height = metaToHeight(meta);
    float heightX = metaToHeight(metaX);
    float heightZ = metaToHeight(metaZ);

    float heightDiff = ((height - heightX) + (height - heightZ)) / lodScale;
    float shade = clamp(heightDiff * 0.06, -0.2, 0.04);

    color.rgb += shade;

    float blockLight = meta.r * 255.0;
    float light = mix(blockLight, 15.0, sunlightStrength);
    color.rgb *= mix(ambientLight, 1.0, light / 15.0);

    fragColor = color;
}
