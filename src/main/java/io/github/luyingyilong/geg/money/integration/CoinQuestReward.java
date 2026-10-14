package io.github.luyingyilong.geg.money.integration;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CoinQuestReward extends Reward {
    private long configuredAmount;

    public CoinQuestReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbCoinIntegration.REWARD_TYPE;
    }

    @Override
    public void readData(CompoundTag tag) {
        super.readData(tag);
        configuredAmount = tag.getLong("coinMinorUnits");
    }

    @Override
    public void writeData(CompoundTag tag) {
        super.writeData(tag);
        tag.putLong("coinMinorUnits", configuredAmount);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        configuredAmount = buffer.readLong();
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeLong(configuredAmount);
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        FtbCoinIntegration.claim(player, this);
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("reward.geg.coin", MoneyCurrency.COIN.format(configuredAmount));
    }

    public long configuredAmount() {
        return configuredAmount;
    }
}
