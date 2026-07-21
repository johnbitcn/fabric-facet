#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:texture_sampling.glsl>
#moj_import <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 rawVertexColor;
in vec2 texCoord0;
in vec3 cameraRelativePosition;

#ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
#endif

const float FACET_LIGHT_RADIUS = 15.0;
const float FACET_LIGHT_BOOST = 0.18;

bool isFacetOutline() {
    return rawVertexColor.a < 0.999;
}

vec4 facetOutlineColor() {
    float distanceToPlayer = length(cameraRelativePosition.xz);
    float boost = 1.0 - smoothstep(0.0, FACET_LIGHT_RADIUS, distanceToPlayer);
    vec3 color = mix(rawVertexColor.rgb, vec3(1.0), boost * FACET_LIGHT_BOOST);
    return vec4(color, rawVertexColor.a);
}

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 color;
    if (isFacetOutline()) {
        color = facetOutlineColor();
    } else {
        color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    }

    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
