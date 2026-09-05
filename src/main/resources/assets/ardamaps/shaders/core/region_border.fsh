#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in float borderSide;
in vec3 borderDash;

out vec4 fragColor;

float smoothstepEdge(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float exploredAlpha() {
    vec2 texelSize = 1.0 / vec2(textureSize(Sampler0, 0));

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
                float maskAlpha = texture(Sampler0, texCoord0 + offset).a;
                float weight = exp(-dist * dist / (2.0 * sigma * sigma));

                weightedAlpha += maskAlpha * weight;
                totalWeight += weight;
            }
        }
    }

    return weightedAlpha / totalWeight;
}

void main() {
    float feather = 0.35;
    float edgeAlpha = borderDash.y > 0.0 ? 1.0 : 1.0 - smoothstepEdge(1.0 - feather, 1.0, abs(borderSide));
    float dashAlpha = 1.0;

    if (borderDash.y > 0.0) {
        float along = (fract(borderDash.x / borderDash.y) - 0.5) * borderDash.y;
        float across = borderSide * borderDash.z;
        float radius = borderDash.z;
        dashAlpha = 1.0 - smoothstepEdge(radius - 0.5, radius + 0.5, length(vec2(along, across)));
    }

    const float VISIBLE_ALPHA = 0.2;
    float explored = clamp(exploredAlpha() / VISIBLE_ALPHA, 0.0, 1.0);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * edgeAlpha * dashAlpha * explored);
}
