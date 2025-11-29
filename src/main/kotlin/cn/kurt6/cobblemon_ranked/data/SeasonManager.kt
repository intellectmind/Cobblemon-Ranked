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
    val rankDao: RankDao,
) {
    private val logger = LoggerFactory.getLogger(SeasonManager::class.java)
    var currentSeasonName: String = ""

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
        currentSeasonName = lastSeason?.seasonName ?: ""

        if (lastSeason == null) {
            currentSeasonId = 1
            startDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            endDate = startDate.plusDays(config.seasonDuration.toLong())
                .withHour(23).withMinute(59).withSecond(59)
            currentSeasonName = ""
            saveSeasonInfo()
        } else {
            currentSeasonId = lastSeason.seasonId
            startDate = LocalDateTime.parse(lastSeason.startDate, dateFormatter)
            endDate = LocalDateTime.parse(lastSeason.endDate, dateFormatter)
            currentSeasonName = lastSeason.seasonName

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
        rankDao.markSeasonEnded(currentSeasonId)

        val allData = rankDao.getAllPlayerData(currentSeasonId)
        allData.forEach {
            it.claimedRanks.clear()
            rankDao.savePlayerData(it)
        }

        currentSeasonId++
        startDate = LocalDateTime.now()
        endDate = startDate.plusDays(config.seasonDuration.toLong())
            .withHour(23)
            .withMinute(59)
            .withSecond(59)

        currentSeasonName = ""
        saveSeasonInfo()
        announceNewSeason(server)
    }

    private fun saveSeasonInfo() {
        rankDao.saveSeasonInfo(
            seasonId = currentSeasonId,
            startDate = formatDate(startDate),
            endDate = formatDate(endDate),
            ended = false,
            name = currentSeasonName
        )
    }

    private fun announceNewSeason(server: MinecraftServer) {
        val lang = config.defaultLang

        server.playerManager.playerList.forEach { player ->
            RankUtils.sendTitle(
                player,
                MessageConfig.get("season.start.title", lang),
                MessageConfig.get("season.start.subtitle", lang, "season" to currentSeasonId.toString(),"name" to currentSeasonName, "start" to formatDate(startDate), "end" to formatDate(endDate)),
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
        override fun toString() = "${days}d ${hours}h ${minutes}m"
    }
}