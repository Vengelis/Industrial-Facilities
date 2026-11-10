#version 150
uniform sampler2D MaskSampler;
uniform vec2 TexelSize;
uniform int CompositeOnly;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    if (CompositeOnly != 0) {
        fragColor = texture(MaskSampler, texCoord) * vertexColor;
        return;
    }
    if (texture(MaskSampler, texCoord).a > 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    float nearest = 100.0;
    vec3 color = vec3(0.0);
    for (int y = -4; y <= 4; y++) {
        for (int x = -4; x <= 4; x++) {
            float distanceSquared = float(x*x + y*y);
            if (distanceSquared > 16.0 || distanceSquared >= nearest) continue;
            vec4 sampleColor = texture(MaskSampler, texCoord + vec2(x,y)*TexelSize);
            if (sampleColor.a > 0.5) {
                nearest = distanceSquared;
                color = sampleColor.rgb;
            }
        }
    }
    float alpha = nearest <= 4.0 ? 0.95 : nearest <= 16.0 ? 0.32*(1.0-sqrt(nearest)/5.0) : 0.0;
    fragColor = vec4(color * alpha, alpha) * vertexColor;
}
