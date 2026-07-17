package com.OneTouchTV

import com.seoulentertainment.app.APIHolder.capitalize
import com.seoulentertainment.app.Actor
import com.seoulentertainment.app.ActorData
import com.seoulentertainment.app.ErrorLoadingException
import com.seoulentertainment.app.HomePageList
import com.seoulentertainment.app.HomePageResponse
import com.seoulentertainment.app.LoadResponse
import com.seoulentertainment.app.MainAPI
import com.seoulentertainment.app.MainPageRequest
import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SearchResponseList
import com.seoulentertainment.app.ShowStatus
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.app
import com.seoulentertainment.app.base64Decode
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.newEpisode
import com.seoulentertainment.app.newHomePageResponse
import com.seoulentertainment.app.newSubtitleFile
import com.seoulentertainment.app.newTvSeriesLoadResponse
import com.seoulentertainment.app.newTvSeriesSearchResponse
import com.seoulentertainment.app.toNewSearchResponseList
import com.seoulentertainment.app.utils.AppUtils.parseJson
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.INFER_TYPE
import com.seoulentertainment.app.utils.getQualityFromName
import com.seoulentertainment.app.utils.newExtractorLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class OneTouchTV : MainAPI() {
    override var mainUrl = base64Decode("aHR0cHM6Ly9hcGkzLmRldmNvcnAubWU=")
    override var name = "OneTouchTV"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "vod/home" to "Home",
    )

    override suspend fun search(query: String,page: Int): SearchResponseList? {
        val url = "$mainUrl/vod/search?page=$page&keyword=$query"
        val responseText = try {
            app.get(url, referer = "$mainUrl/").text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch search data: ${e.message}")
        }

        val decryptedJson = try {
            decryptString(responseText)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val results: List<SearchResult> = try {
            if (decryptedJson.trim().startsWith("[")) {
                parseJson<Array<SearchResult>>(decryptedJson).toList()
            } else {
                parseJson<Search>(decryptedJson).result
            }
        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to parse decrypted JSON: ${e.message}"
            )
        }

        if (results.isEmpty()) {
            throw ErrorLoadingException("No search results found")
        }

        return results.map { result ->
            newTvSeriesSearchResponse(
                result.title ?: "UnKnown",
                "$mainUrl/vod/${result.id}/detail",
                if (result.type.equals("movie", true)) TvType.Movie else TvType.TvSeries
            ) {
                posterUrl = result.image
            }
        }.toNewSearchResponseList()
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val rawResponse = try {
            app.get("$mainUrl/${request.data}").text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch raw response: ${e.message}")
        }

        val decryptedJson = try {
            decryptString(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val parser = try {
            parseJson<MediaResult>(decryptedJson)
        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to parse decrypted JSON: ${e.message}"
            )
        }

        val allRawMedia = buildList {
            addAll(parser.randomSlideShow?.map { it.toCleanMedia() } ?: emptyList())
            addAll(parser.recents?.map { it.toCleanMedia() } ?: emptyList())
        }

        val uniqueMedia = allRawMedia.distinctBy { it.id ?: it.title }

        val filteredMedia = uniqueMedia.filter { media ->
            settingsForProvider.enableAdult || !(media.type?.contains("RAW", ignoreCase = true) ?: false)
        }

        val groupedByCountry = filteredMedia.groupBy { it.country?.trim()?.lowercase() ?: "unknown" }

        val homeLists = groupedByCountry.mapNotNull { (country, items) ->
            if (items.size > 4) {
                HomePageList(
                    name = country.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    list = items.map { it.toSearchResponse(mainUrl) },
                    isHorizontalImages = false
                )
            } else null
        }

        return newHomePageResponse(list = homeLists, hasNext = false)
    }

    private fun OneTouchTVParser.Day.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchTVParser.Week.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchTVParser.Month.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchMedia.toSearchResponse(): SearchResponse {
        return newTvSeriesSearchResponse(title, "$mainUrl/vod/${id}/detail", TvType.Movie) {
            this.posterUrl = image
        }
    }

    data class OneTouchMedia(
        val title: String = "Unknown Title",
        val id: String? = "0",
        val image: String? = null,
        val type: String? = null,
        val country: String? = null,
        val year: String? = null,
        val status: String? = null,
        val isSub: Boolean = false
    )

    private fun RandomSlideShow.toCleanMedia() = CleanMedia(
        id = id2 ?: id,
        title = title,
        image = image,
        country = country,
        type = type,
        year = year,
        status = status,
        isSub = isSub ?: false
    )

    private fun Recent.toCleanMedia() = CleanMedia(
        id = id2 ?: id,
        title = title,
        image = image,
        country = country,
        type = type,
        year = year,
        status = status,
        isSub = isSub ?: false
    )

    private fun CleanMedia.toSearchResponse(mainUrl: String): SearchResponse {
        return newTvSeriesSearchResponse(
            title ?: "Unknown Title",
            "$mainUrl/vod/${id ?: ""}/detail",
            TvType.Movie
        ) {
            this.posterUrl = image
        }
    }


    override suspend fun load(url: String): LoadResponse {

        val rawResponse = try {
            app.get(url).text
        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to fetch raw response: ${e.message}"
            )
        }

        val decryptedJson = try {
            decryptString(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to decrypt response: ${e.message}"
            )
        }

        val parser = try {
            parseJson<LoadData>(decryptedJson)
        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to parse decrypted JSON: ${e.message}"
            )
        }

        val title = parser.title ?: "Unknown Title"

        val poster = parser.image ?: "null"

        val backgroundposter = parser.poster
            ?.replace(
                "image-7wk.pages.dev",
                "image-v1.pages.dev"
            )
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: (parser.image ?: "")

        val description = parser.description ?: ""

        val year = parser.year?.toIntOrNull()

        val status = getStatus(parser.status ?: "")

        val actors = parser.actors.map {
            ActorData(
                Actor(
                    it.name ?: "",
                    it.image ?: ""
                )
            )
        }

        val tags = parser.genres.map {
            it.replaceFirstChar(Char::uppercase)
        }

        val episodes = parser.episodes.mapNotNull { ep ->

            val identifier = ep.identifier ?: return@mapNotNull null
            val playId = ep.playId ?: return@mapNotNull null

            newEpisode(
                "$mainUrl/vod/$identifier/episode/$playId"
            ) {
                name = "Episode ${ep.episode ?: "?"}"
            }
        }

        val recommendation: List<SearchResponse> = try {

            val rawTopResponse = app.get("$mainUrl/vod/top").text

            val topJson = try {
                decryptString(rawTopResponse)
            } catch (e: Exception) {
                throw ErrorLoadingException(
                    "Failed to decrypt response: ${e.message}"
                )
            }

            val topParser = try {
                parseJson<OneTouchTVParser>(topJson)
            } catch (e: Exception) {
                throw ErrorLoadingException(
                    "Failed to parse decrypted JSON: ${e.message}"
                )
            }

            val allMedia = buildList {
                topParser.day?.forEach { add(it.toMedia()) }
                topParser.week?.forEach { add(it.toMedia()) }
                topParser.month?.forEach { add(it.toMedia()) }
            }

            allMedia.map { it.toSearchResponse() }

        } catch (e: Exception) {
            throw ErrorLoadingException(
                "Failed to load recommendations: ${e.message}"
            )
        }

        return newTvSeriesLoadResponse(
            title,
            url,
            type = TvType.TvSeries,
            episodes = episodes.reversed()
        ) {
            this.backgroundPosterUrl = backgroundposter
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.showStatus = status
            this.year = year
            this.actors = actors
            this.recommendations = recommendation
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val rawResponse = try {
            app.get(data).text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch raw response: ${e.message}")
        }
        val decryptedJson = try {
            decryptString(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val (sources, tracks) = parseSourcesAndTracks(decryptedJson)

        launch {
            for (track in tracks) {
                subtitleCallback(
                    newSubtitleFile(
                        track.name ?: "Unknown",
                        track.file ?: continue)
                )
            }
        }

        launch {
            for (src in sources) {
                callback(
                    newExtractorLink(
                        src.name?.capitalize() ?: "Source",
                        src.name?.capitalize() ?: "Source",
                        src.url ?: continue,
                        INFER_TYPE
                    )
                    {
                        this.quality = getQualityFromName(src.quality ?: "")
                        this.headers = src.headers
                    }
                )
            }
        }
        true
    }

    private fun getStatus(t: String): ShowStatus {
        return when (t) {
            "Finished Airing" -> ShowStatus.Completed
            "ongoing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    data class LoadData(
        val title: String? = null,
        val image: String? = null,
        val poster: String? = null,
        val description: String? = null,
        val year: String? = null,
        val status: String? = null,
        val actors: List<ActorItem> = emptyList(),
        val genres: List<String> = emptyList(),
        val episodes: List<EpisodeItem> = emptyList()
    )

    data class ActorItem(
        val name: String? = null,
        val image: String? = null
    )

    data class EpisodeItem(
        val episode: String? = null,
        val identifier: String? = null,
        val playId: String? = null
    )
}
