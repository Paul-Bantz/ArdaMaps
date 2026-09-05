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
in vec4 Color;
in vec2 UV0;
in float BorderSide;
in vec3 BorderDash;

out vec4 vertexColor;
out vec2 texCoord0;
out float borderSide;
out vec3 borderDash;

void main() {
    vertexColor = Color;
    texCoord0 = UV0;
    borderSide = BorderSide;
    borderDash = BorderDash;
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
}
