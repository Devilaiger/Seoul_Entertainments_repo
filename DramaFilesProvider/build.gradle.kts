// use an integer for version numbers
version = 1

android {
    namespace = "com.seoulentertainment.dramafiles"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "en"
    description = "DramaFiles Movies and Series Provider"
    authors = listOf("SeoulEntertainment")
    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    requiresResources = false
}
