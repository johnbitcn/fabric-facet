#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:chunksection.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec4 rawVertexColor;
layout(location = 5) in vec3 cameraRelativePosition;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
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
