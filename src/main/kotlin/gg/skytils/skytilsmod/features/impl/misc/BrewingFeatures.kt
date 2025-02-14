/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2022 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package gg.skytils.skytilsmod.features.impl.misc

import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.events.impl.GuiContainerEvent
import gg.skytils.skytilsmod.events.impl.PacketEvent
import gg.skytils.skytilsmod.utils.*
import net.minecraft.inventory.ContainerChest
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.tileentity.TileEntityBrewingStand
import net.minecraft.util.BlockPos
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.awt.Color

object BrewingFeatures {
    var lastBrewingStand: TileEntityBrewingStand? = null
    val brewingStandToTimeMap = hashMapOf<BlockPos, Long>()
    val timeRegex = Regex("§a(?<sec>\\d+(?:.\\d)?)s")
    private val green = Color(0, 255, 0, 128)
    private val red = Color(255, 0, 0, 128)
    private var ticks = 0

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!Skytils.config.colorBrewingStands || !Utils.inSkyblock || SBInfo.mode != SkyblockIsland.PrivateIsland.mode) return
        if (ticks++ % 100 != 0) return
        brewingStandToTimeMap.entries.removeIf {
            mc.theWorld?.getTileEntity(it.key) !is TileEntityBrewingStand
        }
        ticks = 0
    }

    @SubscribeEvent
    fun onPacketSend(event: PacketEvent.SendEvent) {
        if (!Skytils.config.colorBrewingStands || !Utils.inSkyblock || SBInfo.mode != SkyblockIsland.PrivateIsland.mode) return
        if (event.packet is C08PacketPlayerBlockPlacement && event.packet.position.y != -1) {
            lastBrewingStand = mc.theWorld.getTileEntity(event.packet.position) as? TileEntityBrewingStand ?: return
        }
    }

    @SubscribeEvent
    fun onContainerUpdate(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!Skytils.config.colorBrewingStands || !Utils.inSkyblock || SBInfo.mode != SkyblockIsland.PrivateIsland.mode) return
        if (lastBrewingStand == null || event.container !is ContainerChest || event.chestName != "Brewing Stand") return
        val timeSlot = event.container.getSlot(22).stack ?: return
        val time = timeRegex.find(timeSlot.displayName)?.groups?.get("sec")?.value?.toDoubleOrNull() ?: 0.0
        brewingStandToTimeMap[lastBrewingStand!!.pos] = System.currentTimeMillis() + (time * 1000L).toLong()
    }

    @SubscribeEvent
    fun onWorldDraw(event: RenderWorldLastEvent) {
        if (!Skytils.config.colorBrewingStands || !Utils.inSkyblock || SBInfo.mode != SkyblockIsland.PrivateIsland.mode) return
        val (viewerX, viewerY, viewerZ) = RenderUtil.getViewerPos(event.partialTicks)
        val matrixStack = UMatrixStack()
        val currTime = System.currentTimeMillis()
        brewingStandToTimeMap.forEach { (pos, time) ->
            RenderUtil.drawFilledBoundingBox(
                matrixStack,
                pos.toBoundingBox().expandBlock().offset(-viewerX, -viewerY, -viewerZ),
                if (time > currTime) red else green,
                1f
            )
        }
    }
}