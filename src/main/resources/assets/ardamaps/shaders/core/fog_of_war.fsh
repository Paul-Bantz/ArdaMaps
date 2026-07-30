#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec2 texCoordPaper;

out vec4 fragColor;

void main() {
    vec4 fogTex = texture(Sampler0, texCoordPaper);
    vec2 texelSize = 1.0 / vec2(textureSize(Sampler1, 0));

    float blurRadius = 1.0;
    float sigma = 0.5;
    float sampleStep = 0.25;

    float totalWeight = 0.0;
    float weightedAlpha = 0.0;

    for (float y = -blurRadius; y <= blurRadius; y += sampleStep) {
        for (float x = -blurRadius; x <= blurRadius; x += sampleStep) {
            vec2 offset = vec2(x, y) * texelSize;
            float dist = length(vec2(x, y));

            if (dist <= blurRadius) {
                float maskAlpha = texture(Sampler1, texCoord0 + offset).a;
                float weight = exp(-dist * dist / (2.0 * sigma * sigma));

                weightedAlpha += maskAlpha * weight;
                totalWeight += weight;
            }
        }
    }

    float blendedAlpha = weightedAlpha / totalWeight;
    float fogDarkness = 1.0 - blendedAlpha;

    fragColor = vec4(fogTex.rgb * fogDarkness, fogDarkness);
}
