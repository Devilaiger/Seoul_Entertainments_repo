// use an integer for version numbers
version = 1

android {
    namespace = "com.seoulentertainment.katmoviehd"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "en"
    description = "KatMovieHD Provider (In-House)"
    authors = listOf("SeoulEntertainment")
    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    requiresResources = false
    iconUrl = "https://github.com/Devilaiger/Seoul_Entertainments_repo/raw/refs/heads/master/KatMovieHDProvider/icon.png"
}
