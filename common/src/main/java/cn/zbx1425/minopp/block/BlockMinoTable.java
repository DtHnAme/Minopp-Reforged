package cn.zbx1425.minopp.block;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.gui.WildSelectionScreen;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.network.C2SPlayCardPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlockMinoTable extends Block implements EntityBlock {

    public static final EnumProperty<TablePartType> PART = EnumProperty.create("part", TablePartType.class);

    public BlockMinoTable() {
        super(BlockBehaviour.Properties.of().noOcclusion());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (level.isClientSide && itemStack.is(Mino.ITEM_HAND_CARDS.get())) {
            BlockPos corePos = getCore(blockState, blockPos);
            ItemHandCards.CardGameBindingComponent gameBinding = itemStack.getOrDefault(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get(), ItemHandCards.CardGameBindingComponent.EMPTY);
//            if (gameBinding.tablePos().isEmpty() || !gameBinding.tablePos().get().equals(corePos)) {
//                player.displayClientMessage(Component.translatable("game.minopp.play.no_player"), true);
//                return ItemInteractionResult.FAIL;
//            }
            int handIndex = itemStack.getOrDefault(Mino.DATA_COMPONENT_TYPE_CLIENT_HAND_INDEX.get(), 0);
            CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
            BlockEntity blockEntity = level.getBlockEntity(corePos);
            if (blockEntity instanceof BlockEntityMinoTable tableEntity) {
                if (tableEntity.game != null) {
                    CardPlayer realPlayer = tableEntity.game.deAmputate(playerWithoutHand);
                    if (realPlayer == null) return ItemInteractionResult.FAIL;
                    if (blockState.getValue(PART) == TablePartType.X_LESS_Z_LESS) {
                        C2SPlayCardPacket.Client.sendDrawCardC2S(corePos, playerWithoutHand);
                    } else if (handIndex == realPlayer.hand.size()) {
                        C2SPlayCardPacket.Client.sendPlayNoCardC2S(corePos, playerWithoutHand);
                    } else {
                        Card selectedCard = realPlayer.hand.get(Mth.clamp(handIndex, 0, realPlayer.hand.size() - 1));
                        if (selectedCard.suit() == Card.Suit.WILD) {
                            Minecraft.getInstance().setScreen(new WildSelectionScreen(corePos, playerWithoutHand, selectedCard));
                        } else {
                            C2SPlayCardPacket.Client.sendPlayCardC2S(corePos, playerWithoutHand, selectedCard, null);
                        }
                    }
                }
            }
        }
        return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        BlockPos corePos = getCore(blockState, blockPos);
        BlockEntity blockEntity = level.getBlockEntity(corePos);
        if (blockEntity instanceof BlockEntityMinoTable tableEntity) {
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
            if (player.isSecondaryUseActive() && blockState.getValue(PART) == TablePartType.X_MORE_Z_MORE) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                List<CardPlayer> playersList = tableEntity.getPlayersList();
                if (!playersList.contains(cardPlayer)) {
                    player.displayClientMessage(Component.translatable("game.minopp.play.no_player"), true);
                    return InteractionResult.FAIL;
                }
                if (playersList.size() < 2) {
                    player.displayClientMessage(Component.translatable("game.minopp.play.no_enough_player"), true);
                    return InteractionResult.FAIL;
                }
                // Start or end the game
                if (tableEntity.game == null) {
                    tableEntity.startGame(cardPlayer);
                    level.sendBlockUpdated(corePos, blockState, blockState, 2);
                } else {
                    tableEntity.destroyGame(cardPlayer);
                    level.sendBlockUpdated(corePos, blockState, blockState, 2);
                }
                return InteractionResult.SUCCESS;
            }
            if (tableEntity.game == null) {
                if (level.isClientSide) return InteractionResult.SUCCESS;

                // Join player to table
                BlockPos centerPos = corePos.offset(1, 0, 1);
                Vec3 playerOffset = player.position().subtract(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                Direction playerDirection = Direction.fromYRot(Mth.atan2(playerOffset.z, playerOffset.x) * 180 / Math.PI - 90);
                boolean quitting = false;
                for (Direction checkDir : tableEntity.players.keySet()) {
                    if (cardPlayer.equals(tableEntity.players.get(checkDir))) {
                        tableEntity.players.put(checkDir, null);
                        if (checkDir == playerDirection) quitting = true;
                    }
                }
                if (!quitting) tableEntity.players.put(playerDirection, cardPlayer);
                tableEntity.setChanged();
                level.sendBlockUpdated(corePos, blockState, blockState, 2);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
        BlockPos firstPartPos = blockPlaceContext.getClickedPos();
        Level level = blockPlaceContext.getLevel();
        for (int i = 0; i < 4; i++) {
            TablePartType part = TablePartType.values()[i];
            BlockPos thisPartPos = firstPartPos.offset(part.xOff, 0, part.zOff);
            boolean isPlaceable = level.getBlockState(thisPartPos).canBeReplaced(blockPlaceContext)
                    && level.getWorldBorder().isWithinBounds(thisPartPos);
            if (!isPlaceable) return null;
        }
        return this.defaultBlockState().setValue(PART, TablePartType.X_LESS_Z_LESS);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
        super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
        if (!level.isClientSide) {
            for (int i = 1; i < 4; i++) {
                TablePartType thisPart = TablePartType.values()[i];
                BlockPos thisPartPos = blockPos.offset(thisPart.xOff, 0, thisPart.zOff);
                level.setBlock(thisPartPos, this.defaultBlockState().setValue(PART, thisPart), 3);
            }
        }
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        BlockPos firstPartPos = getCore(blockState, blockPos);
        for (int i = 0; i < 4; i++) {
            TablePartType thisPart = TablePartType.values()[i];
            BlockPos thisPartPos = firstPartPos.offset(thisPart.xOff, 0, thisPart.zOff);
            if (!levelAccessor.getBlockState(thisPartPos).is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }

    public static BlockPos getCore(BlockState blockState, BlockPos blockPos) {
        TablePartType part = blockState.getValue(PART);
        return blockPos.offset(-part.xOff, 0, -part.zOff);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        if (blockState.getValue(PART) != TablePartType.X_LESS_Z_LESS) return null;
        return new BlockEntityMinoTable(blockPos, blockState);
    }

    public enum TablePartType implements StringRepresentable {
        X_LESS_Z_LESS,
        X_LESS_Z_MORE,
        X_MORE_Z_LESS,
        X_MORE_Z_MORE;

        public final int xOff;
        public final int zOff;

        TablePartType() {
            this.xOff = this.ordinal() / 2;
            this.zOff = this.ordinal() % 2;
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase();
        }
    }

    @Override
    protected float getShadeBrightness(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return true;
    }
}
