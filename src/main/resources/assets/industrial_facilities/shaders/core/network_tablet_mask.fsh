#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 maskColor;
out vec4 fragColor;
void main() {
    if (texture(Sampler0, texCoord).a * maskColor.a < 0.1) discard;
    fragColor = vec4(maskColor.rgb, 1.0);
}
