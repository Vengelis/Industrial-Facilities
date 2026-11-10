package fr.vengelis.industrialfacilities.security.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Restore the caller's render target and state after the private silhouette passes. */
final class TabletRenderState implements AutoCloseable {
    private final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport=new int[4],scissor=new int[4];private final boolean[] colorMask=new boolean[4];
    private final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),scissored=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),shaderTexture=RenderSystem.getShaderTexture(0),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int texture;private final float[] shaderColor=RenderSystem.getShaderColor().clone();private final ShaderInstance shader=RenderSystem.getShader();
    private final int element=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final float[] clearColor=new float[4];private final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    TabletRenderState(){
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
        try(var stack=MemoryStack.stackPush()){var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)colorMask[i]=mask.get(i)!=0;}
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);RenderSystem.activeTexture(active);
    }
    void bindCaller(){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);}
    @Override public void close(){
        var current=RenderSystem.getShader();if(current!=null)current.clear();
        bindCaller();RenderSystem.depthFunc(depthFunc);RenderSystem.depthMask(depthMask);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.colorMask(colorMask[0],colorMask[1],colorMask[2],colorMask[3]);if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);GL20.glBlendEquationSeparate(equationRgb,equationAlpha);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
        RenderSystem.enableScissor(scissor[0],scissor[1],scissor[2],scissor[3]);if(!scissored)RenderSystem.disableScissor();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);RenderSystem.setShaderTexture(0,shaderTexture);RenderSystem.activeTexture(active);
        RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);
        RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);RenderSystem.clearDepth(clearDepth);
        BufferUploader.invalidate();GlStateManager._glBindVertexArray(vao);GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,array);if(vao!=0)GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,element);
    }
}
