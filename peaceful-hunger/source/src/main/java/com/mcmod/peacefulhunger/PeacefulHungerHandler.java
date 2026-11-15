package com.mcmod.peacefulhunger;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 和平难度下的饥饿换算补丁（含对原版“回饱”逻辑的抵消）。
 *
 * <p><b>原版行为一</b>（{@code FoodData#tick}）：累积饥饿消耗值超过 4.0 时消耗 4.0 点，
 * 饱和度大于 0 就扣 1 点饱和度，否则<b>只有在非和平难度</b>才扣 1 点饱食度。</p>
 *
 * <p><b>原版行为二</b>（{@code Player#aiStep}）：和平难度且开启自然恢复时，
 * 只要 {@code needsFood()}（饱食度 &lt; 20）成立，每 10 tick 给饱食度 +1。
 * 这就是和平难度“饱食度永远回满”的原因。只补行为一会被行为二立刻抵消，所以两者都要处理。</p>
 *
 * <p>处理方式：tick 开始时（原版逻辑之前）把行为一跳过的饱食度损失补上，
 * 并记录当时的饱食度/饱和度；tick 结束时（原版逻辑之后）识别出这一次的 +1 回饱并撤销。
 * 判定是否属于“回饱”而不是“吃东西”：回饱只加饱食度、不加饱和度，而吃东西一定会加饱和度。</p>
 */
@Mod.EventBusSubscriber(modid = PeacefulHunger.MODID)
public final class PeacefulHungerHandler {

    /** 原版换算比例：4.0 点饥饿消耗值 = 1 点饱食度。 */
    private static final float EXHAUSTION_PER_FOOD_POINT = 4.0F;

    /** 原版回饱的周期：每 10 tick 一次。 */
    private static final int VANILLA_REFILL_PERIOD = 10;

    /** 饱和度比较的容差，用于区分“回饱（饱和度不变）”和“吃东西（饱和度上升）”。 */
    private static final float SATURATION_EPSILON = 0.0001F;

    /** 本 tick 开始时玩家饱食度状态的快照，用于在 tick 结束时识别原版回饱。 */
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private PeacefulHungerHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (player.level().getDifficulty() != Difficulty.PEACEFUL) {
            SNAPSHOTS.remove(player.getUUID());
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            SNAPSHOTS.remove(player.getUUID());
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            applyHungerDrain(player);
            FoodData food = player.getFoodData();
            SNAPSHOTS.put(player.getUUID(), new Snapshot(food.getFoodLevel(), food.getSaturationLevel()));
        } else {
            cancelVanillaRefill(player);
            SNAPSHOTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SNAPSHOTS.remove(event.getEntity().getUUID());
    }

    /** 补上原版在和平难度跳过的“饥饿消耗值 → 饱食度”换算。 */
    private static void applyHungerDrain(Player player) {
        FoodData food = player.getFoodData();
        float exhaustion = food.getExhaustionLevel();
        if (exhaustion <= EXHAUSTION_PER_FOOD_POINT) {
            return;
        }
        // 饱和度还有存量时，原版在和平难度下也会正常扣饱和度，这里不重复处理。
        if (food.getSaturationLevel() > 0.0F) {
            return;
        }

        int points = (int) (exhaustion / EXHAUSTION_PER_FOOD_POINT);
        food.setFoodLevel(Math.max(food.getFoodLevel() - points, 0));
        // 把已经换算掉的消耗值减掉，原版随后的判断（> 4.0 才处理）自然就会跳过。
        food.addExhaustion(-EXHAUSTION_PER_FOOD_POINT * points);
    }

    /** 撤销原版在和平难度下的自动回饱。 */
    private static void cancelVanillaRefill(Player player) {
        Snapshot snapshot = SNAPSHOTS.get(player.getUUID());
        if (snapshot == null || snapshot.foodLevel >= 20) {
            return;
        }
        if (player.tickCount % VANILLA_REFILL_PERIOD != 0) {
            return;
        }
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            return;
        }

        FoodData food = player.getFoodData();
        int expectedAfterRefill = Math.min(snapshot.foodLevel + 1, 20);
        if (food.getFoodLevel() != expectedAfterRefill) {
            return;
        }
        // 饱和度上升说明这一 tick 玩家吃了东西，那 +1 是真实收益，不能撤销。
        if (food.getSaturationLevel() > snapshot.saturationLevel + SATURATION_EPSILON) {
            return;
        }

        food.setFoodLevel(snapshot.foodLevel);
    }

    private record Snapshot(int foodLevel, float saturationLevel) {
    }
}
