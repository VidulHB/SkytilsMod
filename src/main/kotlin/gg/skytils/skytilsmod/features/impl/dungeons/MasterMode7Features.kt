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

package gg.skytils.skytilsmod.features.impl.dungeons

import gg.essential.universal.ChatColor
import gg.essential.universal.UChat
import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.core.GuiManager
import gg.skytils.skytilsmod.events.impl.BlockChangeEvent
import gg.skytils.skytilsmod.events.impl.CheckRenderEntityEvent
import gg.skytils.skytilsmod.events.impl.MainReceivePacketEvent
import gg.skytils.skytilsmod.mixins.extensions.ExtensionEntityLivingBase
import gg.skytils.skytilsmod.mixins.transformers.accessors.AccessorModelDragon
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.graphics.colors.ColorFactory
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderDragon
import net.minecraft.entity.Entity
import net.minecraft.entity.boss.EntityDragon
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.ResourceLocation
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.awt.Color

object MasterMode7Features {

    private val spawningDragons = hashSetOf<WitherKingDragons>()
    private val killedDragons = hashSetOf<WitherKingDragons>()
    private val dragonMap = hashMapOf<Int, WitherKingDragons>()
    private val glowstones = hashSetOf<AxisAlignedBB>()
    private var ticks = 0

    @SubscribeEvent
    fun onBlockChange(event: BlockChangeEvent) {
        if (DungeonTimer.phase4ClearTime == -1L) return
        if (Skytils.config.witherKingDragonSlashAlert) {
            if (event.old.block === Blocks.glowstone) {
                glowstones.clear()
                return
            }
            if (event.update.block === Blocks.glowstone && event.old.block != Blocks.packed_ice) {
                glowstones.add(AxisAlignedBB(event.pos.add(-5, -5, -5), event.pos.add(5, 5, 5)))
            }
        }
        if ((event.pos.y == 18 || event.pos.y == 19) && event.update.block === Blocks.air && event.old.block === Blocks.stone_slab) {
            val dragon = WitherKingDragons.values().find { it.bottomChin == event.pos } ?: return
            dragon.isDestroyed = true
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (DungeonTimer.phase4ClearTime == -1L || DungeonTimer.scoreShownAt != -1L || event.phase != TickEvent.Phase.START || mc.thePlayer == null) return
        if (ticks % 15 == 0) {
            if (Skytils.config.witherKingDragonSlashAlert) {
                if (glowstones.any { it.isVecInside(mc.thePlayer.positionVector) }) {
                    GuiManager.createTitle("Dimensional Slash!", 10)
                }
            }
            ticks = 0
        }
        ticks++
    }

    @SubscribeEvent
    fun onPacket(event: MainReceivePacketEvent<*, *>) {
        if (DungeonTimer.phase4ClearTime == -1L) return
        if (event.packet is S2CPacketSpawnGlobalEntity && event.packet.func_149053_g() == 1) {
            val x = event.packet.func_149051_d() / 32.0
            val y = event.packet.func_149050_e() / 32.0
            val z = event.packet.func_149049_f() / 32.0
            if (x % 1 != 0.0 || y % 1 != 0.0 || z % 1 != 0.0) return
            val drag =
                WitherKingDragons.values().find { it.blockPos.x == x.toInt() && it.blockPos.z == z.toInt() } ?: return
            if (spawningDragons.add(drag)) {
                printDevMessage("${drag.name} spawning", "witherkingdrags")
                if (Skytils.config.witherKingDragonSpawnAlert) UChat.chat("§c§lThe ${drag.chatColor}§l${drag.name} §c§ldragon is spawning! §f(${x}, ${y}, ${z})")
            }
        }
    }

    fun onMobSpawned(entity: Entity) {
        if (DungeonTimer.phase4ClearTime != -1L && entity is EntityDragon) {
            val type =
                dragonMap[entity.entityId] ?: WitherKingDragons.values()
                    .minByOrNull { entity.getXZDistSq(it.blockPos) } ?: return
            (entity as ExtensionEntityLivingBase).skytilsHook.colorMultiplier = type.color
            (entity as ExtensionEntityLivingBase).skytilsHook.masterDragonType = type
            printDevMessage("${type.name} spawned", "witherkingdrags")
            dragonMap[entity.entityId] = type
        }
    }

    @SubscribeEvent
    fun onDeath(event: LivingDeathEvent) {
        if (event.entity is EntityDragon) {
            val item = (event.entity as ExtensionEntityLivingBase).skytilsHook.masterDragonType ?: return
            printDevMessage("${item.name} died", "witherkingdrags")
            spawningDragons.remove(item)
            killedDragons.add(item)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        spawningDragons.clear()
        killedDragons.clear()
        dragonMap.clear()
        glowstones.clear()
        WitherKingDragons.values().forEach { it.isDestroyed = false }
    }

    @SubscribeEvent
    fun onRenderLivingPost(event: RenderLivingEvent.Post<*>) {
        val entity = event.entity
        if (DungeonTimer.phase4ClearTime != -1L && entity is EntityDragon && (Skytils.config.showWitherDragonsColorBlind || Skytils.config.showWitherKingDragonsHP || Skytils.config.showWitherKingStatueBox)) {
            val matrixStack = UMatrixStack()
            entity as ExtensionEntityLivingBase
            GlStateManager.disableCull()
            GlStateManager.disableDepth()
            val text = StringBuilder()
            val percentage = event.entity.health / event.entity.baseMaxHealth
            val color = when {
                percentage >= 0.75 -> ColorFactory.YELLOWGREEN
                percentage >= 0.5 -> ColorFactory.YELLOW
                percentage >= 0.25 -> ColorFactory.DARKORANGE
                else -> ColorFactory.CRIMSON
            }
            if (Skytils.config.showWitherDragonsColorBlind) {
                text.append(entity.skytilsHook.masterDragonType?.textColor)
                text.append(' ')
            }
            if (Skytils.config.showWitherKingDragonsHP) {
                text.append(NumberUtil.format(event.entity.health))
            }
            if (Skytils.config.showWitherKingStatueBox && entity.skytilsHook.masterDragonType?.bb?.isVecInside(entity.positionVector) == true) {
                text.append(" §fR")
            }

            RenderUtil.drawLabel(
                Vec3(
                    RenderUtil.interpolate(entity.posX, entity.lastTickPosX, RenderUtil.getPartialTicks()),
                    RenderUtil.interpolate(entity.posY, entity.lastTickPosY, RenderUtil.getPartialTicks()) + 0.5f,
                    RenderUtil.interpolate(entity.posZ, entity.lastTickPosZ, RenderUtil.getPartialTicks())
                ),
                text.toString(),
                color,
                RenderUtil.getPartialTicks(),
                matrixStack,
                true,
                6f
            )
            GlStateManager.enableDepth()
            GlStateManager.enableCull()
        }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (Skytils.config.showWitherKingStatueBox && DungeonTimer.phase4ClearTime != -1L) {
            for (drag in WitherKingDragons.values()) {
                if (drag.isDestroyed) continue
                RenderUtil.drawOutlinedBoundingBox(drag.bb, drag.color, 3.69f, event.partialTicks)
            }
        }
    }

    @SubscribeEvent
    fun onCheckRender(event: CheckRenderEntityEvent<*>) {
        if (event.entity is EntityDragon && event.entity.deathTicks > 1 && shouldHideDragonDeath()) {
            event.isCanceled = true
        }
    }

    fun getHurtOpacity(
        renderDragon: RenderDragon,
        lastDragon: EntityDragon,
        value: Float
    ): Float {
        if (!Skytils.config.changeHurtColorOnWitherKingsDragons) return value
        lastDragon as ExtensionEntityLivingBase
        return if (lastDragon.skytilsHook.colorMultiplier != null) {
            val model =
                renderDragon.mainModel as AccessorModelDragon
            model.body.isHidden = true
            model.wing.isHidden = true
            0.03f
        } else value
    }

    fun getEntityTexture(entity: EntityDragon, cir: CallbackInfoReturnable<ResourceLocation>) {
        if (!Skytils.config.retextureWitherKingsDragons) return
        entity as ExtensionEntityLivingBase
        val type = entity.skytilsHook.masterDragonType ?: return
        cir.returnValue = type.texture
    }

    fun afterRenderHurtFrame(
        renderDragon: RenderDragon,
        entitylivingbaseIn: EntityDragon,
        f: Float,
        g: Float,
        h: Float,
        i: Float,
        j: Float,
        scaleFactor: Float,
        ci: CallbackInfo
    ) {
        val model =
            renderDragon.mainModel as AccessorModelDragon
        model.body.isHidden = false
        model.wing.isHidden = false
    }

    fun shouldHideDragonDeath() =
        Utils.inDungeons && DungeonTimer.phase4ClearTime != -1L && Skytils.config.hideWitherKingDragonDeath
}

enum class WitherKingDragons(
    val textColor: String,
    val blockPos: BlockPos,
    val color: Color,
    val chatColor: ChatColor,
    val bottomChin: BlockPos,
    var isDestroyed: Boolean = false
) {
    POWER("Red", BlockPos(27, 14, 59), ColorFactory.RED, ChatColor.RED, BlockPos(32, 18, 59)),
    APEX("Green", BlockPos(27, 14, 94), ColorFactory.LIME, ChatColor.GREEN, BlockPos(32, 19, 94)),
    SOUL("Purple", BlockPos(56, 14, 125), ColorFactory.PURPLE, ChatColor.DARK_PURPLE, BlockPos(56, 18, 128)),
    ICE("Blue", BlockPos(84, 14, 94), ColorFactory.CYAN, ChatColor.AQUA, BlockPos(79, 19, 94)),
    FLAME("Orange", BlockPos(85, 14, 56), ColorFactory.CORAL, ChatColor.GOLD, BlockPos(80, 19, 56));

    val itemName = "§cCorrupted $textColor Relic"
    val itemId = "${textColor.uppercase()}_KING_RELIC"
    val texture = ResourceLocation("skytils", "textures/dungeons/m7/dragon_${this.name.lowercase()}.png")
    private val a = 13.5
    val bb = blockPos.run {
        AxisAlignedBB(x - a, y - 8.0, z - a, x + a, y + a + 2, z + a)
    }
}