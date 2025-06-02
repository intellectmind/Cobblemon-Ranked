//package cn.kurt6.cobblemon_ranked.client.config
//
//import cn.kurt6.cobblemon_ranked.config.RankConfig
//import me.shedaniel.clothconfig2.api.ConfigBuilder
//import me.shedaniel.clothconfig2.api.ConfigCategory
//import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
//import net.minecraft.client.gui.screen.Screen
//import net.minecraft.text.Text
//
//class RankConfigScreen(parent: Screen?) : Screen(Text.of("Cobblemon Ranked Config")) {
//    private val parentScreen: Screen? = parent
//    private val config = RankConfig.load()
//
//    override fun init() {
//        val builder = ConfigBuilder.create()
//            .setParentScreen(parentScreen)
//            .setTitle(Text.of("Cobblemon Ranked Configuration"))
//            .setSavingRunnable { RankConfig.save(config) }
//
//        val general = builder.getOrCreateCategory(Text.of("General Settings"))
//        val entryBuilder = builder.entryBuilder()
//
//        // 添加配置项
//        general.addEntry(entryBuilder.startIntField(Text.of("Min Team Size"), config.minTeamSize)
//            .setDefaultValue(1)
//            .setMin(1)
//            .setMax(6)
//            .setTooltip(Text.of("Minimum number of Pokemon required to queue"))
//            .setSaveConsumer { config.minTeamSize = it }
//            .build()
//
//                general.addEntry(entryBuilder.startIntField(Text.of("Max Level"), config.maxLevel)
//            .setDefaultValue(0)
//            .setMin(0)
//            .setMax(100)
//            .setTooltip(Text.of("Maximum Pokemon level allowed (0 = no limit)"))
//            .setSaveConsumer { config.maxLevel = it }
//            .build()
//
//                general.addEntry(entryBuilder.startIntField(Text.of("K-Factor"), config.kFactor)
//            .setDefaultValue(32)
//            .setMin(10)
//            .setMax(100)
//            .setTooltip(Text.of("ELO calculation sensitivity"))
//            .setSaveConsumer { config.kFactor = it }
//            .build()
//
//            // 添加禁用宝可梦列表
//            val bannedList = entryBuilder.startStrList(Text.of("Banned Pokemon"), config.bannedPokemon)
//        .setDefaultValue(listOf("Mewtwo", "Arceus"))
//            .setTooltip(Text.of("Pokemon that cannot be used in ranked battles"))
//            .setSaveConsumer { config.bannedPokemon = it }
//            .build()
//
//        general.addEntry(bannedList)
//
//        // 添加赛季设置
//        val seasonCat = builder.getOrCreateCategory(Text.of("Season Settings"))
//
//        seasonCat.addEntry(entryBuilder.startIntField(Text.of("Season Duration (days)"), config.seasonDuration)
//            .setDefaultValue(56)
//            .setMin(7)
//            .setMax(365)
//            .setTooltip(Text.of("How long each season lasts in days"))
//            .setSaveConsumer { config.seasonDuration = it }
//            .build()
//
//                // 显示当前屏幕
//                client?.setScreen(builder.build())
//    }
//}