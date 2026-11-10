#version 150
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
in vec4 haloColor;
in float haloDistance;
out vec4 fragColor;
void main() {
    float fade = 1.0 - smoothstep(FogStart, max(FogStart + 0.001, FogEnd), haloDistance);
    fragColor = haloColor * ColorModulator * fade;
}
