package com.sealant.chamber.client;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslTree;

/** Fragment-only clipping of actual water atlas UVs; glass/other translucent blocks survive.
 * Reconstruct camera-relative world position from depth, independent of Sable's model transform.
 * No WorldOcclusionContainer, voxel edits or per-frame chunk rebuilds are involved. */
public final class ChamberWaterMaskShader implements ShaderPreProcessor {
    public static final int LIMIT=16;
    public static final String DECLARATIONS="""
            uniform int SealantDryCount;
            uniform mat4 SealantDryInverseVP;
            uniform vec2 SealantDryScreen;
            uniform vec4 SealantDryPlanes[48];
            uniform vec4 SealantDryHalf[16];
            uniform vec4 SealantWaterUV[3];
            """;
    public static final String CLIP="""
            if (SealantDryCount > 0) {
                bool sealantIsWater = false;
                for (int sealantUV = 0; sealantUV < 3; sealantUV++) {
                    vec4 r = SealantWaterUV[sealantUV];
                    if (texCoord0.x > r.x && texCoord0.y > r.y && texCoord0.x < r.z && texCoord0.y < r.w) sealantIsWater = true;
                }
                if (sealantIsWater) {
                    vec4 q = SealantDryInverseVP * vec4(gl_FragCoord.xy / SealantDryScreen * 2.0 - 1.0, gl_FragCoord.z * 2.0 - 1.0, 1.0);
                    vec4 p = vec4(q.xyz / q.w, 1.0);
                    for (int sealantBox = 0; sealantBox < 16; sealantBox++) {
                        if (sealantBox >= SealantDryCount) break;
                        vec3 local = vec3(dot(SealantDryPlanes[sealantBox*3], p), dot(SealantDryPlanes[sealantBox*3+1], p), dot(SealantDryPlanes[sealantBox*3+2], p));
                        if (all(lessThan(abs(local), SealantDryHalf[sealantBox].xyz))) discard;
                    }
                }
            }
            """;
    @Override public void modify(Context context,GlslTree tree) throws GlslSyntaxException {
        if(!context.isSourceFile() || !context.isFragment() || !(context instanceof MinecraftContext mc)
                || !mc.shaderInstance().equals("rendertype_translucent"))return;
        inject(tree);
    }
    static void inject(GlslTree tree) throws GlslSyntaxException {
        tree.getBody().addAll(GlslInjectionPoint.BEFORE_MAIN,GlslParser.parseExpressionList(DECLARATIONS));
        tree.mainFunction().orElseThrow().getBody().add(0,GlslParser.parseExpression(CLIP));
    }
}
