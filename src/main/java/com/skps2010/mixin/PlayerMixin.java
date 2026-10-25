package com.skps2010.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skps2010.CravingManager;
import com.skps2010.FDConfigs;
import com.skps2010.FoodHistoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge/Mojmap equivalent of the Fabric FoodComponentMixin.
 * In 1.21.1, Player#eat(Level, ItemStack, FoodProperties) calls FoodData#eat(FoodProperties);
 * we wrap that call and hand it a boosted/reduced copy of the food.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Unique
    private static float caf$mult(boolean craving, int eatenBefore) {
        var c = FDConfigs.CFG;
        if (craving) return c.cravingMultiplier;
        for (var r : c.rules)
            if (r.maxCount < 0 || eatenBefore <= r.maxCount) return r.multiplier;
        return 1f;
    }

    @WrapOperation(
            method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"
            )
    )
    private void caf$eatWithBoost(FoodData foodData, FoodProperties original, Operation<Void> op,
                                  @Local(argsOnly = true) ItemStack stack) {
        // Not a real server player: vanilla behaviour
        if (!((Object) this instanceof ServerPlayer player) || player instanceof FakePlayer) {
            op.call(foodData, original);
            return;
        }

        // Decide "craving" BEFORE counting this bite, so the bite that hits the
        // per-round limit still gets the craving bonus (the reroll happens right after).
        boolean craving = CravingManager.isCraving(player, stack.getItem());

        // Craving round counter / expiry (rerolls and notifies the client when needed)
        CravingManager.onConsume(player, stack);

        int eatenBefore = FoodHistoryManager.recordAndGetCount(player, CravingManager.idOf(stack.getItem())) - 1;
        if (eatenBefore < 0) eatenBefore = 0;

        float m = caf$mult(craving, eatenBefore);
        if (m == 1f) {
            op.call(foodData, original);
            return;
        }

        FoodProperties boosted = new FoodProperties(
                Math.round(original.nutrition() * m),
                original.saturation() * m,
                original.canAlwaysEat(),
                original.eatSeconds(),
                original.usingConvertsTo(),
                original.effects()
        );
        op.call(foodData, boosted);
    }
}
