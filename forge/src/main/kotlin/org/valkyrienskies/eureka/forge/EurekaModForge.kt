package org.valkyrienskies.eureka.forge

import net.minecraft.core.registries.Registries
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.registries.DeferredRegister
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaMod
import org.valkyrienskies.eureka.EurekaMod.init
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckLandingApplier
import org.valkyrienskies.eureka.registry.CreativeTabs
import org.valkyrienskies.eureka.forge.registry.FuelRegistryImpl
import thedarkcolour.kotlinforforge.forge.LOADING_CONTEXT
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.runForDist

@Mod(EurekaMod.MOD_ID)
class EurekaModForge {
    init {
        runForDist (
            clientTarget = {
                EurekaModForgeClient.registerClient()
            },
            serverTarget = {}
        )
        LOADING_CONTEXT.apply {
            registerConfig(ModConfig.Type.SERVER, EurekaConfig.EUREKA_SPEC, "valkyrienskies/vs_eureka.toml")
        }
        MOD_BUS.addListener(::onConfigReload)
        MOD_BUS.addListener(::onConfigLoad)
        FuelRegistryImpl()
        MinecraftForge.EVENT_BUS.addListener(::onServerTick)
        MinecraftForge.EVENT_BUS.addListener(::onClientTick)
        init()

        val deferredRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EurekaMod.MOD_ID)
        deferredRegister.register("general") {
            CreativeTabs.create()
        }
        deferredRegister.register(getModBus())
    }

    companion object {
        fun getModBus(): IEventBus = MOD_BUS
    }

    private fun onConfigLoad(event: ModConfigEvent.Loading) {
        if (event.config.modId == EurekaMod.MOD_ID) {
            EurekaConfig.update(event.config)
        }
    }

    private fun onConfigReload(event: ModConfigEvent.Reloading) {
        if (event.config.modId == EurekaMod.MOD_ID) {
            EurekaConfig.update(event.config)
        }
    }

    private fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            ShipDeckLandingApplier.pinAllWelds()
        }
    }

    private fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            ShipDeckLandingApplier.pinAllWelds()
        }
    }
}
