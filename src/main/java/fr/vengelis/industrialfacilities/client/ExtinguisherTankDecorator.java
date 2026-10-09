package fr.vengelis.industrialfacilities.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

@EventBusSubscriber(
    modid = IndustrialFacilitiesMod.MODID,
    value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD
)
public class ExtinguisherTankDecorator {

    private static final double MAX_TANK = 6400.0;

    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(
            IndustrialFacilitiesModItems.EXTINGUISHER.get(),
            new TankBarDecorator()
        );
    }

    private static class TankBarDecorator implements IItemDecorator {

        @Override
        public boolean render(
            GuiGraphics guiGraphics,
            Font font,
            ItemStack stack,
            int x,
            int y
        ) {
            double tank = stack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getDouble("tank");

            tank = Math.max(0.0, Math.min(MAX_TANK, tank));

            double ratio = tank / MAX_TANK;

            int width = 13;
            int height = 1;

            int filled = (int) Math.round(width * ratio);

            int barX = x + 2;
            int barY = y + 14;

            guiGraphics.pose().pushPose();

            // Passe clairement devant le rendu de l'item
            guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);

            // Fond
            guiGraphics.fill(
                barX,
                barY,
                barX + width,
                barY + height,
                0xFF1A1A1A
            );

            // Partie remplie
            if (filled > 0) {
                guiGraphics.fill(
                    barX,
                    barY,
                    barX + filled,
                    barY + height,
                    getTankColor(ratio)
                );
            }

            guiGraphics.pose().popPose();

            return false;
        }

        private static int getTankColor(double ratio) {
            if (ratio > 0.50) {
                return 0xFF55FF55; // vert
            } else if (ratio > 0.20) {
                return 0xFFFFFF55; // jaune
            } else {
                return 0xFFFF5555; // rouge
            }
        }
    }
}