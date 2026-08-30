package com.megix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap
import android.util.Log

open class MoviesmodProvider : MainAPI() {
    override var mainUrl = "https://moviesmod.zone"
    override var name = "Moviesmod"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    val cinemeta_url = "https://aiometadata.elfhosted.com/stremio/9197a4a9-2f5b-4911-845e-8704c520bdf7/meta"
    private val cfKiller by lazy { CloudflareKiller() }
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime
    )

    private var cachedDomain: String? = null

    private suspend fun getBaseDomain(): String {
        if (cachedDomain != null) return cachedDomain!!
        val resolved = try {
            val doc = app.get("https://mmodlist.org/").document
            val domain = doc.select("span.badge:contains(moviesmod)").firstOrNull()?.text()?.trim()
                ?: doc.select("a[href*='type=hollywood']").firstOrNull()?.parent()?.selectFirst("span.badge")?.text()?.trim()
            if (!domain.isNullOrBlank() && domain.startsWith("http")) domain.removeSuffix("/") else null
        } catch (_: Exception) { null }

        val finalDomain = resolved ?: try {
            val response = app.get("https://raw.githubusercontent.com/SaurabhKaperwan/Utils/refs/heads/main/urls.json")
            val json = response.text
            val jsonObject = JSONObject(json)
            val opt = jsonObject.optString("moviesmod")
            if (opt.isNotBlank()) opt.removeSuffix("/") else null
        } catch (_: Exception) { null } ?: mainUrl

        cachedDomain = finalDomain
        mainUrl = finalDomain
        return finalDomain
    }

    override val mainPage = mainPageOf(
        "/page/" to "Home",
        "/web-series/on-going/page/" to "Latest Web Series",
        "/movies/page/" to "Latest Movies",
        "/animated-web-series/page/" to "Anime",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val baseUrl = getBaseDomain()
        val document = app.get(baseUrl + request.data + page, interceptor = cfKiller).document
        val home = document.select("div.post-cards > article, article.post-item, article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    fun Element.toSearchResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href").takeIf { it.isNotBlank() } ?: return null
        val title = aTag.attr("title").takeIf { it.isNotBlank() }?.replace("Download ", "")?.trim()
            ?: this.selectFirst("h2, h3, .entry-title, a")?.text()?.replace("Download ", "")?.trim()
            ?: return null
        if (title.isBlank()) return null

        val img = this.selectFirst("img")
        val posterUrl = img?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("src")?.takeIf { it.isNotBlank() }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val baseUrl = getBaseDomain()
        val cleanQuery = query.trim()
        val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
        val searchDoc = try {
            val searchUrl = if (page <= 1) "$baseUrl/search/$encodedQuery/" else "$baseUrl/search/$encodedQuery/page/$page"
            app.get(searchUrl, interceptor = cfKiller).document
        } catch (_: Exception) {
            val fallbackUrl = if (page <= 1) "$baseUrl/?s=$encodedQuery" else "$baseUrl/page/$page/?s=$encodedQuery"
            app.get(fallbackUrl, interceptor = cfKiller).document
        }

        val results = searchDoc.select("div.post-cards > article, article.post-item, article").mapNotNull { 
            it.toSearchResult() 
        }
        val hasNext = results.isNotEmpty()
        return newSearchResponseList(results, hasNext)
    }

    override suspend fun load(url: String): LoadResponse? {
        val baseUrl = getBaseDomain()
        val fixedUrl = if (url.startsWith("http")) url else "$baseUrl/$url"
        val document = app.get(fixedUrl, interceptor = cfKiller).document
        var title = document.select("meta[property=og:title]").attr("content").replace("Download ", "")
        if (title.contains(" - MoviesMod")) {
            title = title.substringBefore(" - MoviesMod").trim()
        }
        val ogTitle = title
        var posterUrl = document.select("meta[property=og:image]").attr("content")
        var description = document.select("div.imdbwp__teaser").text()
        val div = document.select("div.thecontent").text()
        val tvtype = if (div.contains("season", ignoreCase = true) || div.contains("episode", ignoreCase = true)) "series" else "movie"
        val imdbUrl = document.select("a[href*=\"imdb.com\"]").attr("href")
        val imdbId = imdbUrl.substringAfter("title/").substringBefore("/")
        val jsonResponse = if (imdbId.isNotBlank()) {
            try {
                app.get("$cinemeta_url/$tvtype/$imdbId.json").text
            } catch (_: Exception) {
                null
            }
        } else null
        val responseData = jsonResponse?.let { tryParseJson<ResponseData>(it) }

        var cast: List<String> = emptyList()
        var genre: List<String> = emptyList()
        var imdbRating: String = ""
        var year: String = ""
        var background: String = posterUrl

        if(responseData != null) {
            description = responseData.meta.description ?: description
            cast = responseData.meta.cast ?: emptyList()
            title = responseData.meta.name ?: title
            genre = responseData.meta.genre ?: emptyList()
            imdbRating = responseData.meta.imdbRating ?: ""
            year = responseData.meta.year ?: ""
            posterUrl = responseData.meta.poster ?: posterUrl
            background = responseData.meta.background ?: background
        }

        if(tvtype == "series") {
            if(title != ogTitle) {
                val checkSeason = Regex("""Season\s*\d*1|S\s*\d*1""").find(ogTitle)
                if (checkSeason == null) {
                    val seasonText = Regex("""Season\s*\d+|S\s*\d+""").find(ogTitle)?.value
                    if(seasonText != null) {
                        title = title + " " + seasonText
                    }
                }
            }

            val tvSeriesEpisodes = mutableListOf<Episode>()
            val episodesMap = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, MutableList<String>>()
            val buttons = document.select("a.maxbutton-episode-links,.maxbutton-g-drive,.maxbutton-af-download")

            kotlinx.coroutines.supervisorScope {
                buttons.map { button ->
                    async {
                        runCatching {
                            var link = button.attr("href")
                            val seasonText = button.parent()?.previousElementSibling()?.text().orEmpty()
                            val realSeason = Regex("""(?:Season |S)(\d+)""")
                                .find(seasonText)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull() ?: 0

                            if (link.contains("url=")) {
                                val base64Value = link.substringAfter("url=")
                                link = base64Decode(base64Value)
                            }

                            val doc = app.get(link).document
                            val hTags = doc.select("h3,h4")
                            var e = 1

                            hTags.forEach { hTag ->
                                val epUrl = hTag.select("a").attr("href").takeIf { it.isNotBlank() } ?: return@forEach
                                val key = Pair(realSeason, e)
                                episodesMap.compute(key) { _, current ->
                                    (current ?: mutableListOf()).apply { add(epUrl) }
                                }
                                e++
                            }
                        }
                    }
                }.forEach { it.await() }
            }

            for ((key, value) in episodesMap) {
                val episodeInfo = responseData?.meta?.videos?.find { it.season == key.first && it.episode == key.second }
                val data = value.map { source->
                    EpisodeLink(
                        source
                    )
                }
                tvSeriesEpisodes.add(
                    newEpisode(data) {
                        this.name = episodeInfo?.name ?: episodeInfo?.title
                        this.season = key.first
                        this.episode = key.second
                        this.posterUrl = episodeInfo?.thumbnail
                        this.description = episodeInfo?.overview
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, tvSeriesEpisodes) {
                this.posterUrl = posterUrl
                this.plot = description
                this.tags = genre
                this.score = Score.from10(imdbRating)
                this.year = year.toIntOrNull()
                this.backgroundPosterUrl = background
                addActors(cast)
                addImdbUrl(imdbUrl)
            }
        }
        else {
            val data = document.select("a.maxbutton-download-links, a.maxbutton-1, a.maxbutton-5").mapNotNull {
                var link = it.attr("href")
                if(link.contains("url=")) {
                    val base64Value = link.substringAfter("url=")
                    link = base64Decode(base64Value)
                }

                val doc = app.get(link).document
                val source = doc.select("a.maxbutton-1, a.maxbutton-5, div.text-center > a").attr("href")
                if (source.isNotBlank()) {
                    EpisodeLink(source)
                } else null
            }
            return newMovieLoadResponse(title, url, TvType.Movie, data) {
                this.posterUrl = posterUrl
                this.plot = description
                this.tags = genre
                this.score = Score.from10(imdbRating)
                this.year = year.toIntOrNull()
                this.backgroundPosterUrl = background
                addActors(cast)
                addImdbUrl(imdbUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val sources = parseJson<ArrayList<EpisodeLink>>(data)
        sources.amap {
            var source = it.source
            if(source.contains("unblocked")) {
                source = bypass(source).toString()
            }

            if(source.contains("driveseed") || source.contains("driveleech") || source.contains("animeflix.dad/getlink")) {
                Driveleech().getUrl(source, "", subtitleCallback, callback)
            } else {
                loadExtractor(source, "", subtitleCallback, callback)
            }
        }
        return true
    }

    data class Meta(
        val id: String?,
        val imdb_id: String?,
        val type: String?,
        val poster: String?,
        val logo: String?,
        val background: String?,
        val moviedb_id: Int?,
        val name: String?,
        val description: String?,
        val genre: List<String>?,
        val releaseInfo: String?,
        val status: String?,
        val runtime: String?,
        val cast: List<String>?,
        val language: String?,
        val country: String?,
        val imdbRating: String?,
        val slug: String?,
        val year: String?,
        val videos: List<EpisodeDetails>?
    )

    data class EpisodeDetails(
        val id: String?,
        val name: String?,
        val title: String?,
        val season: Int?,
        val episode: Int?,
        val released: String?,
        val overview: String?,
        val thumbnail: String?,
        val moviedb_id: Int?
    )

    data class ResponseData(
        val meta: Meta
    )

    data class EpisodeLink(
        val source: String
    )
}
