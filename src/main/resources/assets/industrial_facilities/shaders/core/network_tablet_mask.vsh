#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 MaskProjection;
out vec2 texCoord;
out vec4 maskColor;
void main() {
    gl_Position = MaskProjection * vec4(Position, 1.0);
    texCoord = UV0;
    maskColor = Color;
}
