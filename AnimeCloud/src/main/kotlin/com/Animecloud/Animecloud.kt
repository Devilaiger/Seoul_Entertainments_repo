package com.animecloud

import com.seoulentertainment.app.Episode
import com.seoulentertainment.app.ErrorLoadingException
import com.seoulentertainment.app.HomePageList
import com.seoulentertainment.app.HomePageResponse
import com.seoulentertainment.app.LoadResponse
import com.seoulentertainment.app.LoadResponse.Companion.addImdbUrl
import com.seoulentertainment.app.MainAPI
import com.seoulentertainment.app.MainPageRequest
import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.app
import com.seoulentertainment.app.fixUrlNull
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.newAnimeSearchResponse
import com.seoulentertainment.app.newEpisode
import com.seoulentertainment.app.newHomePageResponse
import com.seoulentertainment.app.newMovieSearchResponse
import com.seoulentertainment.app.newTvSeriesLoadResponse
import com.seoulentertainment.app.utils.ExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.seoulentertainment.app.amap

class Animecloud : MainAPI() {
    override var mainUrl              = "https://fireani.me"
    override var name                 = "Animecloud"
    override val hasMainPage          = true
    override var lang                 = "de"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "ListAnimesByViewCount" to "Trending",
        "ListAnimesByGenre|Action" to "Action",
        "ListAnimesByGenre|Drama" to "Drama",
        "ListAnimesByGenre|Scifi" to "Scifi",
        "ListAnimesByGenre|Mystery" to "Mystery",
        "ListAnimesByGenre|Romanze" to "Romanze",
        "ListAnimesByGenre|Abenteuer" to "Abenteuer",
        "ListAnimesByGenre|EngSub" to "EngSub",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = "$mainUrl/api.v1.anime.AnimeService/${request.data.substringBeforeLast("|")}"
        val json = if (request.data.contains("|"))"""
            {
            "page": $page,
            "genre": "${request.data.substringAfterLast("|")}"
            }
        """.trimMargin() else """
           {
            "page": $page
            }
        """.trimIndent()

        val response = app.post(url, requestBody = json.toRequestBody("application/json".toMediaType())).parsedSafe<Home>()
            ?: return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = emptyList(),
                    isHorizontalImages = false
                ),
                hasNext = false
            )

        val home = response.data.map { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = page < response.pages
        )
    }

    private fun HomeDaum.toSearchResult(): SearchResponse {
        val href = this.slug
        val posterslug= this.poster
        return newMovieSearchResponse(
            name = this.title,
            url = href,
            type = TvType.Movie
        ) {
            posterUrl = fixUrlNull("$mainUrl/img/posters/${posterslug}")
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val json = """
        {
            "q": "$query"
        }
        """.trimIndent()
        val searchResponse = app.post("$mainUrl/api.v1.AnimeSearchService/SearchAnimes", requestBody = json.toRequestBody("application/json".toMediaType())).parsedSafe<SearchDaum>()
            ?.data?.map { it.toSearchResponse() }
        return searchResponse
    }

    private fun Search.toSearchResponse(): SearchResponse {
        val title=this.title
        val poster= fixUrlNull("$mainUrl/img/posters/${this.poster}")
        val href= this.slug
        return newAnimeSearchResponse(title, href, TvType.TvSeries,
        ) {
            this.posterUrl=poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val json = """
        {
            "slug": "${url.substringAfterLast("/")}"
        }
        """.trimIndent()

        val document = app.post("$mainUrl/api.v1.anime.AnimeService/GetAnime",
            requestBody = json.toRequestBody("application/json".toMediaType())
        ).parsedSafe<EpisodeParser>()?.data
            ?: throw ErrorLoadingException("Failed to load anime")

        val title = document.title
        val poster = document.poster?.let { "$mainUrl/img/posters/$it" }
        val backgroundUrl = document.backdrop?.let { "$mainUrl/img/posters/bg-$it.webp" }

        val episodes = mutableListOf<Episode>()

        document.animeSeasons.forEach { seasonInfo ->
            val season = if (seasonInfo.season.contains("Filme", true)) {
                0
            } else {
                seasonInfo.season.toIntOrNull()
            }

            val searchSeason = if (season == 0) {
                "Filme"
            } else {
                seasonInfo.season
            }

            seasonInfo.animeEpisodes.forEach { ep ->
                val episodeNumber = ep.episode.toIntOrNull()
                val href = "$mainUrl/&slug=${document.slug}&season=$searchSeason&episode=${ep.episode}"
                episodes += newEpisode(href) {
                    this.name = "Episode ${ep.episode}"
                    this.season = season
                    this.episode = episodeNumber
                    this.posterUrl = ep.image?.let { "$mainUrl/img/thumbs/$it" }
                }
            }
        }

        return newTvSeriesLoadResponse(
            title, url, TvType.Anime, episodes
        ) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backgroundUrl
            this.plot = document.desc
            this.tags = document.generes
            document.imdb?.let {
                addImdbUrl(it)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val json = """
        {
            "slug": "${data.substringAfterLast("&slug=").substringBefore("&")}",
            "season": "${data.substringAfterLast("&season=").substringBefore("&")}",
            "episode": "${data.substringAfterLast("&episode=").substringBefore("&")}"
        }
        """.trimIndent()

        app.post("$mainUrl/api.v1.anime.AnimeService/GetEpisode", requestBody = json.toRequestBody("application/json".toMediaType())).
        parsedSafe<Loadlinks>()?.data?.animeEpisodeLinks?.amap {
            val dubtype=it.lang
            val href=it.link
            loadSourceNameExtractor("$name ${dubtype.uppercase()}",href, "", subtitleCallback, callback, quality = "1080P")
        }
        return true
    }
}
