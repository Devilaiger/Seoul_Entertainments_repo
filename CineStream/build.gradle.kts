import org.jetbrains.kotlin.konan.properties.Properties

version = 470
android {
    defaultConfig {
        val properties = Properties().apply {
            val localFile = project.rootProject.file("local.properties")
            if (localFile.exists()) {
                localFile.inputStream().use { load(it) }
            }
        }
        fun getSecret(key: String): String {
            return properties.getProperty(key) ?: System.getenv(key) ?: ""
        }
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"${getSecret("SIMKL_API")}\"")
        buildConfigField("String", "TMDB_KEY", "\"${getSecret("TMDB_KEY")}\"")
        buildConfigField("String", "CC_COOKIE", "\"${getSecret("CC_COOKIE")}\"")
        buildConfigField("String", "CASTLE_KEY", "\"${getSecret("CASTLE_KEY")}\"")
        buildConfigField("String", "MOVIEBLAST_TOKEN", "\"${getSecret("MOVIEBLAST_TOKEN")}\"")
        buildConfigField("String", "MOVIEBLAST_API", "\"${getSecret("MOVIEBLAST_API")}\"")
        buildConfigField("String", "MOVIEBLAST_KEY", "\"${getSecret("MOVIEBLAST_KEY")}\"")
    }
}

cloudstream {
    language = "en"
    description = "One stop solution for Movies, Series, Anime, AsianDrama and Torrents"
    authors = listOf("megix", "seoulentertainments")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )

    iconUrl = "https://raw.githubusercontent.com/Devilaiger/Seoul_Entertainments_repo/master/CineStream/icon.png"
}
