package cn.zbx1425.minopp.neoforge;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.MinoCommand;
import cn.zbx1425.minopp.entity.EntityAutoPlayer;
import cn.zbx1425.minopp.neoforge.compat.touhou_little_maid.MemoryTypeRegister;
import cn.zbx1425.minopp.neoforge.compat.touhou_little_maid.PoiRegistry;
import cn.zbx1425.minopp.platform.neoforge.CompatPacketRegistry;
import cn.zbx1425.minopp.platform.neoforge.RegistriesWrapperImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Mino.MOD_ID)
public final class MinoNeoForge {

    private static final RegistriesWrapperImpl registries = new RegistriesWrapperImpl();
    public static final CompatPacketRegistry PACKET_REGISTRY = new CompatPacketRegistry();

    public MinoNeoForge(IEventBus eventBus) {
        Mino.init(registries);

        registries.registerAllDeferred(eventBus);
        eventBus.register(RegistriesWrapperImpl.RegisterCreativeTabs.class);
        eventBus.register(ModEventBusListener.class);
        NeoForge.EVENT_BUS.register(ForgeEventBusListener.class);

        // Touhou Little Maid compat
        PoiRegistry.POI_TYPES.register(eventBus);
        MemoryTypeRegister.MEMORY_MODULE_TYPES.register(eventBus);

        if (FMLEnvironment.dist.isClient()) {
            MinoClient.init();
            eventBus.register(ClientProxy.ModEventBusListener.class);
            NeoForge.EVENT_BUS.register(ClientProxy.ForgeEventBusListener.class);
        }
    }

    public static class ModEventBusListener {

        @SubscribeEvent
        public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("1");
            MinoNeoForge.PACKET_REGISTRY.commit(registrar);
        }

        @SubscribeEvent
        public static void newEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(Mino.ENTITY_AUTO_PLAYER.get(), EntityAutoPlayer.createAttributes());
        }
    }

    public static class ForgeEventBusListener {

        @SubscribeEvent
        public static void onRegisterCommands(final RegisterCommandsEvent event) {
            MinoCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onServerChatMessage(final ServerChatEvent event) {
            Mino.onServerChatMessage(event.getRawText(), event.getPlayer());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onAttackEntity(final AttackEntityEvent event) {
            Mino.onPlayerAttackEntity(event.getTarget(), event.getEntity());
        }
    }
}
