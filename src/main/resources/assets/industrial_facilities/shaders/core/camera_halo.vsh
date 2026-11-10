#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 haloColor;
out float haloDistance;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    haloDistance = length(view.xyz);
    haloColor = Color;
}
