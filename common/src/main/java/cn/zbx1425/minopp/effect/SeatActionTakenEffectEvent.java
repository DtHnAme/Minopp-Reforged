package cn.zbx1425.minopp.effect;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.gui.SeatControlScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public record SeatActionTakenEffectEvent() implements EffectEvent {

    @Override
    public Type<? extends EffectEvent> type() {
        return EffectEvents.SEAT_ACTION_TAKEN;
    }

    @Override
    public int timeOffset() {
        return 0;
    }

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public void summonClient(Level level, BlockPos origin, boolean selfIsPartOfSourceGame) {
        if (selfIsPartOfSourceGame && Minecraft.getInstance().screen instanceof SeatControlScreen screen) {
            // Another player is starting / stopping the game
            // Close the screen for better player awareness
            screen.onClose();
        }
    }

    @Override
    public void summonServer(ServerLevel level, BlockPos origin, BlockEntityMinoTable tableEntity) {

    }

    @Override
    public void encode(EffectEvent event, FriendlyByteBuf buffer) {

    }

    public static SeatActionTakenEffectEvent decode(FriendlyByteBuf buffer) {
        return new SeatActionTakenEffectEvent();
    }
}
