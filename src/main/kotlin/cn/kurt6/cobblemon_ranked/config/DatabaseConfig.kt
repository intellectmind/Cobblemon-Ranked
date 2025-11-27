package cn.kurt6.cobblemon_ranked.config

import blue.endless.jankson.Comment

data class DatabaseConfig(
    @Comment("Database type: 'sqlite' or 'mysql' / 数据库类型：'sqlite' 或 'mysql'")
    var databaseType: String = "sqlite",

    @Comment("SQLite database file path (relative to config folder) / SQLite 数据库文件路径")
    var sqliteFile: String = "ranked.db",

    @Comment("MySQL configuration / MySQL 配置")
    var mysql: MySQLConfig = MySQLConfig()
)

data class MySQLConfig(
    @Comment("MySQL host address / MySQL 主机地址")
    var host: String = "localhost",

    @Comment("MySQL port / MySQL 端口")
    var port: Int = 3306,

    @Comment("MySQL database name / MySQL 数据库名")
    var database: String = "cobblemon_ranked",

    @Comment("MySQL username / MySQL 用户名")
    var username: String = "root",

    @Comment("MySQL password / MySQL 密码")
    var password: String = "",

    @Comment("MySQL connection pool size / MySQL 连接池大小")
    var poolSize: Int = 10,

    @Comment("MySQL connection timeout (ms) / MySQL 连接超时时间（毫秒）")
    var connectionTimeout: Long = 5000,

    @Comment("Additional MySQL connection parameters / MySQL 额外连接参数")
    var parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
)