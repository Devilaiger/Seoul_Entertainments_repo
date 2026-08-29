version = 2

cloudstream {
    language = "hi"
    description = "Bollywood and Hindi Dubbed Movies and Series"
    authors = listOf("SeoulEntertainment")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama"
    )

    iconUrl = "https://raw.githubusercontent.com/Devilaiger/Seoul_Entertainments_repo/builds/BollySeoul/icon.png"
}
