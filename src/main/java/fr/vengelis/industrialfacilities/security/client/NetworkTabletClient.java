package fr.vengelis.industrialfacilities.security.client;

import java.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import fr.vengelis.industrialfacilities.access.ui.TabletLinksMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.GL11;

/** A short-lived, read-only view of server-authorized links. No client block-entity searches. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class NetworkTabletClient {
    // Draw this inspection overlay after world geometry, without testing or writing depth.
    // NO_DEPTH_TEST alone is a no-op in 1.21.1, so explicitly restore the previous GL state.
    private static final RenderType LINK_RENDER_TYPE=RenderType.create("industrial_tablet_lightning_xray",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,
        RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setDepthTestState(seeThroughDepth()).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static RenderStateShard.DepthTestStateShard seeThroughDepth(){
        return new RenderStateShard.DepthTestStateShard("industrial_tablet_xray",GL11.GL_ALWAYS){
            private boolean previousDepth;
            @Override public void setupRenderState(){previousDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);RenderSystem.disableDepthTest();}
            @Override public void clearRenderState(){if(previousDepth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();}
        };
    }
    public static final int TTL_TICKS=40;
    private static final int MAX_EDGES=TerminalLinks.MAX_LINKS;
    private static final Set<String> SOURCES=Set.of("reader","automation"),TARGETS=Set.of("door","camera","alarm","emitter","panel");
    private static ClientLevel world;
    private static ResourceLocation dimension;
    private static List<TerminalLinks.Link> cached=List.of();
    private static long tick,receivedAt=-TTL_TICKS,sequence=Long.MIN_VALUE;
    private static boolean truncated;
    private static int rendered;
    private static int renderedOutlines;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event){TabletLinksMessages.receiver=NetworkTabletClient::receive;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;dimension=null;sequence=Long.MIN_VALUE;clearCache();}
    private static boolean held(){var player=Minecraft.getInstance().player;return player!=null&&player.isAlive()&&TerminalLinks.holdingTablet(player);}
    private static void clearCache(){cached=List.of();truncated=false;receivedAt=-TTL_TICKS;rendered=0;renderedOutlines=0;}
    private static void context(){
        var level=Minecraft.getInstance().level;
        if(level!=world){world=level;dimension=level==null?null:level.dimension().location();sequence=Long.MIN_VALUE;clearCache();}
        if(level==null||!held()||tick-receivedAt>=TTL_TICKS)clearCache();
    }
    public static void receive(TabletLinksMessages.State state){
        context();
        if(world==null||!held()||!Objects.equals(dimension,state.dimension())){clearCache();return;}
        if(state.sequence()<=sequence)return;
        if(state.sequence()<0||state.links().size()>MAX_EDGES){clearCache();return;}
        for(var link:state.links()){
            if(link==null||link.sourceRoot()==null||link.targetRoot()==null||link.sourceId()==null||link.targetId()==null||link.sourceKind()==null||link.targetKind()==null
                ||!SOURCES.contains(link.sourceKind())||!TARGETS.contains(link.targetKind())
                ||!TerminalLinks.inRange(link.sourceRoot(),link.targetRoot())){clearCache();return;}
        }
        sequence=state.sequence();receivedAt=tick;cached=List.copyOf(state.links());truncated=state.truncated();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){tick++;context();}
    public static List<TerminalLinks.Link> currentLinks(){
        context();if(world==null||cached.isEmpty())return List.of();
        return cached.stream().filter(link->world.hasChunkAt(link.sourceRoot())&&world.hasChunkAt(link.targetRoot())).toList();
    }
    public static boolean fresh(){context();return world!=null&&held()&&tick-receivedAt<TTL_TICKS;}
    public static long lastSequence(){return sequence;}
    public static int renderedLinkCount(){return rendered;}
    public static int renderedOutlineCount(){return renderedOutlines;}
    public static List<NetworkTabletOutlines.Outline> currentOutlines(){var links=currentLinks();return NetworkTabletOutlines.collect(world,links);}
    public static int colorFor(String source,String target){
        if(source.equals("reader"))return 0x62DDE7;
        return switch(target){case "camera"->0xBE94F0;case "door"->0x81D6A3;case "alarm"->0xF0B064;case "emitter"->0xEE777D;case "panel"->0xF5DB8D;default->0xBE94F0;};
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        rendered=0;renderedOutlines=0;List<TerminalLinks.Link> links=currentLinks();if(links.isEmpty())return;
        var mc=Minecraft.getInstance();Vec3 eye=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();pose.pushPose();pose.mulPose(event.getModelViewMatrix());pose.translate(-eye.x,-eye.y,-eye.z);
        var buffers=mc.renderBuffers().bufferSource();VertexConsumer out=buffers.getBuffer(LINK_RENDER_TYPE);
        Set<UUID> sourceCaps=new HashSet<>(),targetCaps=new HashSet<>();
        for(var link:links){
            Vec3 from=link.sourceRoot().getCenter().add(0,.75,0),to=link.targetRoot().getCenter();
            if(!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(from,to).inflate(.1)))continue;
            int color=colorFor(link.sourceKind(),link.targetKind());beam(pose,out,from,to,eye,color);rendered++;
            if(sourceCaps.add(link.sourceId()))cap(pose,out,from,eye,link.sourceKind().equals("reader")?0x62DDE7:0xBE94F0);
            if(targetCaps.add(link.targetId()))cap(pose,out,to,eye,colorFor("automation",link.targetKind()));
        }
        buffers.endBatch(LINK_RENDER_TYPE);
        pose.popPose();
        renderedOutlines=NetworkTabletSilhouettes.render(event,NetworkTabletOutlines.collect(world,links));
    }
    private static void beam(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,Vec3 eye,int color){
        Vec3 axis=to.subtract(from);if(axis.lengthSqr()<1.0e-8)return;axis=axis.normalize();
        Vec3 side=axis.cross(eye.subtract(from.add(to).scale(.5)));
        if(side.lengthSqr()<1.0e-8)side=axis.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1.0e-8)side=axis.cross(new Vec3(1,0,0));
        side=side.normalize();Vec3 across=axis.cross(side).normalize();
        for(Vec3 normal:List.of(side,across)){
            ribbon(pose,out,from,to,normal.scale(.034),color,20);
            ribbon(pose,out,from,to,normal.scale(.020),color,42);
            int bright=(((color>>16&255)+255)/2<<16)|(((color>>8&255)+255)/2<<8)|(((color&255)+255)/2);
            ribbon(pose,out,from,to,normal.scale(.0065),bright,220);
        }
    }
    private static void ribbon(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,Vec3 side,int color,int alpha){
        Vec3 a=from.subtract(side),b=to.subtract(side),c=to.add(side),d=from.add(side);
        vertex(pose,out,a,color,alpha);vertex(pose,out,b,color,alpha);vertex(pose,out,c,color,alpha);vertex(pose,out,d,color,alpha);
        vertex(pose,out,d,color,alpha);vertex(pose,out,c,color,alpha);vertex(pose,out,b,color,alpha);vertex(pose,out,a,color,alpha);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 point,int color,int alpha){out.addVertex(pose.last(),(float)point.x,(float)point.y,(float)point.z).setColor(color>>16&255,color>>8&255,color&255,alpha);}
    private static void cap(PoseStack pose,VertexConsumer out,Vec3 center,Vec3 eye,int color){
        for(Vec3 axis:List.of(new Vec3(.045,0,0),new Vec3(0,.045,0),new Vec3(0,0,.045)))beam(pose,out,center.subtract(axis),center.add(axis),eye,color);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null||mc.level==null||!held())return;
        var g=event.getGuiGraphics();List<TerminalLinks.Link> links=currentLinks();
        Component heading=fresh()?Component.translatable("gui.industrial_facilities.tablet.links",links.size()):Component.translatable("gui.industrial_facilities.tablet.waiting");
        g.drawString(mc.font,heading,8,8,0xDCC7F1,true);
        int x=8;String[] keys={"access","camera","door","alarm","emitter","panel"};String[] kinds={"door","camera","door","alarm","emitter","panel"};
        for(int i=0;i<keys.length;i++){int color=colorFor(i==0?"reader":"automation",kinds[i]);String label=Component.translatable("gui.industrial_facilities.tablet."+keys[i]).getString();g.fill(x,23,x+3,26,0xFF000000|color);g.drawString(mc.font,label,x+6,20,0xD6D1DC,true);x+=mc.font.width(label)+14;}
        if(truncated)g.drawString(mc.font,Component.translatable("gui.industrial_facilities.tablet.partial"),8,32,0xE9BD85,true);
    }
    private NetworkTabletClient(){}
}
