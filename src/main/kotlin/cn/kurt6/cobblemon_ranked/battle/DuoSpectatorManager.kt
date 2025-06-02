// DuoSpectatorManager.kt
// 观战管理
package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object DuoSpectatorManager {
    // 存储BossBar实例的变量
    private var bossBar: Any? = null

    /**
     * 显示对战BossBar
     * @param server Minecraft服务器实例
     * @param p1 玩家1实体
     * @param p2 玩家2实体
     */
    fun showBattleBar(server: MinecraftServer, p1: ServerPlayerEntity, p2: ServerPlayerEntity) {
        // 获取默认语言配置
        val lang = CobblemonRanked.config.defaultLang
        // 构建BossBar标题文本，包含玩家1和玩家2的名字
        val title = Text.literal(MessageConfig.get("duo.bossbar.title", lang))
            .append(Text.literal(p1.name.string).formatted(Formatting.GREEN)) // 玩家1名字显示为绿色
            .append(Text.literal(" vs ")) // 中间添加" vs "文字
            .append(Text.literal(p2.name.string).formatted(Formatting.RED)) // 玩家2名字显示为红色

        try {
            // 使用反射获取Minecraft的BossBar相关类
            val serverBossBarClass = Class.forName("net.minecraft.class_3242") // ServerBossBar 映射
            val bossBarColorEnum = Class.forName("net.minecraft.class_3288")   // BossBar$Color
            val bossBarOverlayEnum = Class.forName("net.minecraft.class_3290") // BossBar$Overlay

            // 设置BossBar颜色为黄色，样式为10段进度条
            val color = bossBarColorEnum.enumConstants[3]   // YELLOW
            val overlay = bossBarOverlayEnum.enumConstants[1] // NOTCHED_10

            // 获取ServerBossBar的构造方法
            val constructor = serverBossBarClass.getConstructor(
                Text::class.java,
                bossBarColorEnum,
                bossBarOverlayEnum
            )

            // 创建新的BossBar实例
            val newBar = constructor.newInstance(title, color, overlay)
            // 获取添加玩家的方法
            val addPlayerMethod = serverBossBarClass.getMethod("method_14072", Class.forName("net.minecraft.class_3222"))

            // 如果已有BossBar存在，先移除所有玩家
            if (bossBar != null) {
                val removeMethod = serverBossBarClass.getMethod("method_14075", Class.forName("net.minecraft.class_3222"))
                server.playerManager.playerList.forEach { player ->
                    removeMethod.invoke(bossBar, player)
                }
            }

            // 更新BossBar实例
            bossBar = newBar

            // 为所有在线玩家添加BossBar
            server.playerManager.playerList.forEach { player ->
                addPlayerMethod.invoke(bossBar, player)
            }

        } catch (e: Exception) {
            println("BossBar 加载失败: ${e.message}")
        }
    }

    /**
     * 清除对战BossBar
     * @param server Minecraft服务器实例
     */
    fun clearBattleBar(server: MinecraftServer) {
        try {
            // 如果存在BossBar，则移除所有玩家
            if (bossBar != null) {
                val bar = bossBar!!
                val removeMethod = bar.javaClass.getMethod("method_14075", Class.forName("net.minecraft.class_3222"))
                server.playerManager.playerList.forEach { player ->
                    removeMethod.invoke(bar, player)
                }
            }
        } catch (e: Exception) {
            println("清除 BossBar 失败: ${e.message}")
        }
        // 清空BossBar引用
        bossBar = null
    }
}