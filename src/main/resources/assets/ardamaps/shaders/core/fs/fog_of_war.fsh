#version 150

uniform sampler2D FogTex;     // texture unit 0
uniform sampler2D FogMaskTex;   // texture unit 1
uniform vec2 FogTexScale;
uniform vec2 ZoomCenter;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 fogTex = texture(FogTex, (texCoord - ZoomCenter) * FogTexScale + ZoomCenter);
    vec2 texelSize = 1.0 / vec2(textureSize(FogMaskTex, 0));

    // Fade distance in texels - smaller = tighter transition zone
    float blurRadius = 1.0;
    // Gaussian spread - smaller = sharper falloff within the radius
    float sigma = 0.5;
    float sampleStep = 0.25;

    float totalWeight = 0.0;
    float weightedAlpha = 0.0;

    // Gaussian blur: sample surrounding texels and weight by proximity
    for (float y = -blurRadius; y <= blurRadius; y += sampleStep) {
        for (float x = -blurRadius; x <= blurRadius; x += sampleStep) {
            vec2 offset = vec2(x, y) * texelSize;
            float dist = length(vec2(x, y));

            if (dist <= blurRadius) {

                float maskAlpha = texture(FogMaskTex, texCoord + offset).a;

                // Tight Gaussian: falls off quickly for a short but smooth fade
                float weight = exp(-dist * dist / (2.0 * sigma * sigma));

                weightedAlpha += maskAlpha * weight;
                totalWeight += weight;
            }
        }
    }

    // Average the sampled alpha values
    float blendedAlpha = weightedAlpha / totalWeight;

    // Convert to fog darkness: 0.0 (hidden) -> 1.0 (dark), 1.0 (revealed) -> 0.0 (clear)
    float fogDarkness = 1.0 - blendedAlpha;

    fragColor = vec4(fogTex.rgb * fogDarkness, fogDarkness);

    /*vec4 fogTex = texture(FogTex, (texCoord - ZoomCenter) * FogTexScale + ZoomCenter);
    vec2 texelSize = 1.0 / vec2(textureSize(FogMaskTex, 0));

    // Sample radius in TEXTURE SPACE (not mask pixels)
    // This means we sample sub-pixel positions in the mask
    float worldRadius = 2.0; // Controls fade distance in mask texels
    float sampleStep = 0.25; // Sub-pixel sampling (0.25 = 4 samples per texel)

    float minDist = worldRadius + 1.0;
    bool foundRevealed = false;

    // Sample in a grid with sub-texel precision
    for (float y = -worldRadius; y <= worldRadius; y += sampleStep) {
        for (float x = -worldRadius; x <= worldRadius; x += sampleStep) {
            vec2 offset = vec2(x, y) * texelSize;
            float maskValue = texture(FogMaskTex, texCoord + offset).r;

            if (maskValue > 0.5) {
                float dist = length(vec2(x, y));
                minDist = min(minDist, dist);
                foundRevealed = true;
            }
        }
    }

    float alpha;
    if (foundRevealed) {
        // Gradient fades within worldRadius
        alpha = smoothstep(0.0, worldRadius, minDist);
    } else {
        alpha = 1;
    }

    fragColor = vec4(fogTex.rgb * alpha, alpha);*/
}