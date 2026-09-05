#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec2 texCoordMask;

out vec4 fragColor;

void main() {
    vec4 icon = texture(Sampler0, texCoord0);
    fragColor = vec4(icon.rgb, icon.a * texture(Sampler1, texCoordMask).a);
}
