#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 TileParams;

out vec2 texCoord0;
flat out vec4 tileParams;

void main() {
    texCoord0 = UV0;
    tileParams = TileParams;
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
}
