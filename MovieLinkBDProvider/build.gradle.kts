// use an integer for version numbers
version = 3

android {
    namespace = "com.cncverse"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "bn"

    description = "MovieLinkBD Provider"
    authors = listOf("seoulentertainments")

    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
}
