// use an integer for version numbers
version = 3

android {
    namespace = "com.seoulentertainment.movielinkbd"
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
