// SeasonManager.kt
// 赛季管理
package cn.kurt6.cobblemon_ranked.data

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SeasonManager(
    private val rankDao: RankDao,
    private val rewardManager: RewardManager
) {
    private val logger = LoggerFactory.getLogger(SeasonManager::class.java)
    private val config = CobblemonRanked.config

    var currentSeasonId: Int = 1
        private set
    lateinit var startDate: LocalDateTime
        private set
    lateinit var endDate: LocalDateTime
        private set

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        initializeSeasonDates()
    }

    private fun initializeSeasonDates() {
        val lastSeason = rankDao.getLastSeasonInfo()

        if (lastSeason == null) {
            // 全新安装，创建第一个赛季
            currentSeasonId = 1
            startDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            endDate = startDate.plusDays(config.seasonDuration.toLong())
                .withHour(23).withMinute(59).withSecond(59)
            saveSeasonInfo()
        } else {
            // 加载现有赛季
            currentSeasonId = lastSeason.seasonId
            startDate = LocalDateTime.parse(lastSeason.startDate, dateFormatter)
            endDate = LocalDateTime.parse(lastSeason.endDate, dateFormatter)

            // 如果赛季已结束但未处理，自动结束赛季
            if (lastSeason.ended) {
                logger.warn("检测到未处理的赛季结束，需要手动处理赛季 ${lastSeason.seasonId}")
            }
        }
    }

    fun checkSeasonEnd(server: MinecraftServer) {
        if (LocalDateTime.now().isAfter(endDate)) {
            endSeason(server)
        }
    }

    fun endSeason(server: MinecraftServer) {
        // 标记旧赛季已结束
        rankDao.markSeasonEnded(currentSeasonId)

        // 清空所有玩家的段位奖励记录
        val allData = rankDao.getAllPlayerData(currentSeasonId)
        allData.forEach {
            it.claimedRanks.clear()
            rankDao.savePlayerData(it)
        }

        // 开始新赛季
        currentSeasonId++
        startDate = LocalDateTime.now()
        endDate = startDate.plusDays(config.seasonDuration.toLong())
            .withHour(23)
            .withMinute(59)
            .withSecond(59)

        // 保存赛季信息到数据库
        saveSeasonInfo()
        announceNewSeason(server)
    }

    private fun saveSeasonInfo() {
        rankDao.saveSeasonInfo(
            seasonId = currentSeasonId,
            startDate = formatDate(startDate),
            endDate = formatDate(endDate),
            ended = false
        )
    }

    private fun announceNewSeason(server: MinecraftServer) {
        val lang = config.defaultLang
        server.playerManager.playerList.forEach { player ->
            RankUtils.sendTitle(
                player,
                MessageConfig.get("season.start.title", lang),
                MessageConfig.get("season.start.subtitle", lang, "season" to currentSeasonId.toString(), "start" to formatDate(startDate), "end" to formatDate(endDate)),
                20, 100, 20
            )
        }
    }

    fun getRemainingTime(): SeasonRemainingTime {
        val now = LocalDateTime.now()
        if (now.isAfter(endDate)) return SeasonRemainingTime(0, 0, 0)

        val duration = Duration.between(now, endDate)
        return SeasonRemainingTime(
            days = duration.toDays(),
            hours = duration.toHours() % 24,
            minutes = duration.toMinutes() % 60
        )
    }

    fun formatDate(date: LocalDateTime): String = dateFormatter.format(date)

    data class SeasonRemainingTime(
        val days: Long,
        val hours: Long,
        val minutes: Long
    ) {
        override fun toString() = "${days}天${hours}小时${minutes}分钟"
    }
}