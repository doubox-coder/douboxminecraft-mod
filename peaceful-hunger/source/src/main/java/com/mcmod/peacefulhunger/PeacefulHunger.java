package com.mcmod.peacefulhunger;

import net.minecraftforge.fml.common.Mod;

/**
 * 主模组类。
 *
 * <p>模组目的：原版《我的世界》在和平难度下，玩家累积的“饥饿消耗值（exhaustion）”不会换算成饱食度损失，
 * 只有饱和度会被扣，饱和度到 0 后饱食度就永远停在原值。这导致搭配美食/农业类模组时，
 * 和平难度下吃东西毫无意义。本模组把这段被原版跳过的换算补回来。</p>
 */
@Mod(PeacefulHunger.MODID)
public final class PeacefulHunger {

    public static final String MODID = "peacefulhunger";

    public PeacefulHunger() {
    }
}
