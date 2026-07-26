// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.

package com.byayzen

import com.byayzen.MovixAnimeExtractor.fetchAnimeLinks
import com.byayzen.MovixLinks.videolinks
//import com.byayzen.Nakastream.Nakaoynat
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

class Movix : MainAPI() {
    override var mainUrl: String
        get() = runBlocking { MovixHelper.updatemainurl() }
        set(value) {}
    override var name = "Movix"
    override val hasMainPage = true
    override var lang = "fr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "movie/now_playing" to "Nouveaux Films",
        "tv/on_the_air" to "Nouvelles Séries",
        "discover/movie?with_watch_providers=8&watch_region=FR" to "Netflix Films",
        "discover/tv?with_watch_providers=8&watch_region=FR" to "Netflix Séries",
        "discover/movie?with_watch_providers=119&watch_region=FR" to "Prime Video Films",
        "discover/tv?with_watch_providers=119&watch_region=FR" to "Prime Video Séries",
        "discover/movie?with_watch_providers=337&watch_region=FR" to "Disney+ Films",
        "discover/tv?with_watch_providers=337&watch_region=FR" to "Disney+ Séries",
        "movie/28" to "Action",
        "movie/12" to "Aventure",
        "movie/16" to "Animation",
        "movie/35" to "Comédie",
        "movie/80" to "Crime",
        "movie/99" to "Documentaire",
        "movie/18" to "Drame",
        "movie/10751" to "Famille",
        "movie/14" to "Fantastique",
        "movie/36" to "Histoire",
        "movie/27" to "Horreur",
        "movie/9648" to "Mystère",
        "movie/10749" to "Romance",
        "movie/878" to "Science-Fiction",
        "movie/53" to "Thriller",
        "movie/10752" to "Guerre",
        "tv/10759" to "Action et Aventure",
        "tv/35" to "Comédie TV",
        "tv/80" to "Crime TV",
        "tv/18" to "Drame TV",
        "tv/10751" to "Famille TV",
        "tv/10762" to "Enfants",
        "tv/9648" to "Mystère TV",
        "tv/10763" to "Actualités",
        "tv/10764" to "Téléréalité",
        "tv/10765" to "SF et Fantastique",
        "tv/10766" to "Feuilleton"
    )

    private fun TmdbResult.toMainPageResult(type: String): SearchResponse? {
        val titleText =
            this.title ?: this.name ?: this.original_title ?: this.original_name ?: return null
        val poster = (this.poster_path ?: this.backdrop_path)?.let { "$tmdbimg500$it" }
        return if (type == "movie") {
            newMovieSearchResponse(titleText, "$type/$id", TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(vote_average)
            }
        } else {
            newTvSeriesSearchResponse(titleText, "$type/$id", TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(vote_average)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val d = request.data
        val t = if (d.contains("movie")) "movie" else "tv"

        val url = when {
            d.contains("?") -> "$tmdbbase/$d&api_key=$tmdbkey&language=$tmdblang&page=$page"
            d.split("/").last().toIntOrNull() != null -> {
                val id = d.split("/").last()
                "$tmdbbase/discover/$t?api_key=$tmdbkey&language=$tmdblang&with_genres=$id&page=$page&sort_by=popularity.desc&include_adult=false"
            }
            else -> "$tmdbbase/$d?api_key=$tmdbkey&language=$tmdblang&page=$page"
        }

        val res = app.get(url).parsed<TmdbMainResponse>()
        val home = res.results.mapNotNull { it.toMainPageResult(t) }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "$tmdbbase/search/multi?api_key=$tmdbkey&query=$query&language=$tmdblang&page=$page"
        val res = app.get(url).parsed<TmdbMainResponse>()
        val results = res.results.mapNotNull {
            val type = it.media_type ?: return@mapNotNull null
            if (type == "person") return@mapNotNull null
            it.toMainPageResult(type)
        }
        return newSearchResponseList(results, hasNext = page < (res.total_pages ?: 0))
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").last()
        val type = if (url.contains("movie")) "movie" else "tv"
        val currentlang = tmdblang.split("-").first()

        val tmdburl =
            "$tmdbbase/$type/$id?api_key=$tmdbkey&language=$tmdblang&append_to_response=credits,recommendations,videos,images&include_image_language=$currentlang,en,null"
        val res = app.get(tmdburl).parsed<TmdbDetailResponse>()

        val titleText =
            res.title ?: res.name ?: res.original_title ?: res.original_name ?: return null
        val poster = res.poster_path?.let { "$tmdbimg500$it" }
        val bg = res.backdrop_path?.let { "$tmdbimg1280$it" }

        val logo = res.images?.logos?.let { logos ->
            logos.firstOrNull { it.iso_639_1 == currentlang }?.file_path
                ?: logos.firstOrNull { it.iso_639_1 == "en" }?.file_path
                ?: logos.firstOrNull()?.file_path
        }?.let { "$tmdbimg500$it" }

        val yearText =
            (res.release_date ?: res.first_air_date)?.split("-")?.first()?.toIntOrNull()
        val actors = res.credits?.cast?.mapNotNull {
            Actor(
                it.name ?: return@mapNotNull null,
                it.profile_path?.let { p -> "$tmdbimg185$p" }) to it.character
        } ?: emptyList()

        val trailer =
            res.videos?.results?.firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
                ?.let { "https://www.youtube.com/embed/${it.key}" }

        val isAnime =
            res.origin_country?.contains("JP") == true && res.genres?.any { it.id == 16 } == true
        val tvType =
            if (isAnime) TvType.Anime else if (type == "movie") TvType.Movie else TvType.TvSeries

        return if (type == "movie") {
            newMovieLoadResponse(titleText, url, tvType, url) {
                this.posterUrl = poster; this.backgroundPosterUrl = bg; this.logoUrl = logo
                this.plot = res.overview; this.year = yearText
                this.tags = res.genres?.mapNotNull { it.name }
                this.score = Score.from10(res.vote_average)
                this.recommendations = res.recommendations?.results?.mapNotNull { it.toMainPageResult(type) }
                addActors(actors); addTrailer(trailer)
            }
        } else {
            val episodes = res.seasons?.filter { (it.season_number ?: 0) > 0 }?.flatMap { s ->
                val sn = s.season_number ?: 0
                val seasonurl = "$tmdbbase/tv/$id/season/$sn?api_key=$tmdbkey&language=$tmdblang"
                app.get(seasonurl).parsed<TmdbSeasonDetail>().episodes?.map { e ->
                    newEpisode("$type/$id-$sn-${e.episode_number}") {
                        this.name = e.name; this.season = sn; this.episode = e.episode_number
                        this.description = e.overview; this.score = Score.from10(e.vote_average)
                        this.runTime = e.runtime
                        this.posterUrl = e.still_path?.let { "$tmdbimg500$it" } ?: poster
                        this.date = e.air_date?.let {
                            try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }
                            catch (_: Exception) { null }
                        }
                    }
                } ?: emptyList()
            } ?: emptyList()

            newTvSeriesLoadResponse(titleText, url, tvType, episodes) {
                this.posterUrl = poster; this.backgroundPosterUrl = bg; this.logoUrl = logo
                this.showStatus = when (res.status) {
                    "Returning Series", "In Production", "Planned" -> ShowStatus.Ongoing
                    "Canceled", "Ended" -> ShowStatus.Completed
                    else -> null
                }
                this.plot = res.overview; this.year = yearText
                this.tags = res.genres?.mapNotNull { it.name }
                this.score = Score.from10(res.vote_average)
                this.recommendations = res.recommendations?.results?.mapNotNull { it.toMainPageResult(type) }
                addActors(actors); addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val subCallback: (SubtitleFile) -> Unit = { sub ->
            val langName = SubtitleHelper.fromTagToEnglishLanguageName(sub.lang) ?: sub.lang
            launch { subtitleCallback(newSubtitleFile(langName, sub.url)) }
        }

        val domainResult = MovixHelper.updatemainurl()
        val currentUrl = if (domainResult.isNotBlank()) domainResult else mainUrl
        Log.d("movix", "Loadlinks domaini: $currentUrl")

        val domainsilici = currentUrl
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .removeSuffix("/")
        val apibase = if (domainsilici.isNotBlank()) "https://api.$domainsilici/api" else "https://api.movix.show/api"

        val parts = data.split("-")
        val rawpath = parts[0]
        val id = rawpath.substringAfterLast("/")
        val type = if (rawpath.contains("movie")) "movie" else "tv"

        val season = parts.getOrNull(1)
        val episode = parts.getOrNull(2)
        val query = if (type == "tv" && season != null && episode != null) "?season=$season&episode=$episode" else ""

        val apiheaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0",
            "Origin" to "https://$domainsilici",
            "Referer" to "https://$domainsilici/"
        )

        // Anime detection: launch in background if applicable
        try {
            val tmdbres = app.get("$tmdbbase/$type/$id?api_key=$tmdbkey").parsed<TmdbDetailResponse>()
            val title = tmdbres.title ?: tmdbres.name ?: tmdbres.original_title ?: tmdbres.original_name
            val isanime = tmdbres.origin_country?.contains("JP") == true && tmdbres.genres?.any { it.id == 16 } == true
            if (isanime && !title.isNullOrBlank()) {
                launch {
                    val animelinks = fetchAnimeLinks(mainUrl, apibase, title, type, episode, season)
                    MovixLinks.processlinks("Anime", animelinks, mainUrl, subCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.d("movix", "Tmdb linklerinde hata var: ${e.message}")
        }

        // Active API-backed requests
        val apiRequests = if (type == "movie") listOf(
            "Frembed" to "https://frembed.click/api/public/v1/movies/$id"
        ) else listOf(
            "Frembed" to "https://frembed.click/api/public/v1/tv/$id?sa=$season&epi=$episode"
        )

        // Embed player providers — routed to registered extractors or loadExtractor
        val embedProviders = if (type == "movie") listOf(
            "Vidsrc.io"    to "https://vidsrc.io/embed/movie/$id",
            "Peachify"     to "https://peachify.top/embed/movie/$id",
            "SmashyStream" to "https://player.smashystream.com/movie/$id",
            "AutoEmbed"    to "https://autoembed.co/movie/tmdb/$id",
            "2Embed"       to "https://2embed.cc/embed/$id",
            "MultiEmbed"   to "https://multiembed.mov/directstream.php?video_id=$id&tmdb=1"
        ) else listOf(
            "Vidsrc.io"    to "https://vidsrc.io/embed/tv/$id/$season/$episode",
            "Peachify"     to "https://peachify.top/embed/tv/$id/$season/$episode",
            "SmashyStream" to "https://player.smashystream.com/tv/$id/$season/$episode",
            "AutoEmbed"    to "https://autoembed.co/tv/tmdb/$id-$season-$episode",
            "2Embed"       to "https://2embed.cc/embedtv/$id&$season&$episode",
            "MultiEmbed"   to "https://multiembed.mov/directstream.php?video_id=$id&tmdb=1&s=$season&e=$episode"
        )

        // All API + embed providers run fully in parallel
        supervisorScope {
            val apiJobs = apiRequests.map { (brandname, targeturl) ->
                async {
                    try {
                        Log.d("movix", "API: $brandname -> $targeturl")
                        val res = app.get(targeturl, headers = apiheaders, timeout = 15)
                        val response = if (res.code in listOf(301, 302)) {
                            res.headers["location"]?.let { app.get(it, headers = apiheaders, timeout = 15).text } ?: res.text
                        } else res.text

                        if (MovixLinks.isvalidresponse(response)) {
                            when (brandname) {
                                "Frembed" -> MovixLinks.parsefrembed(response, mainUrl, subCallback, callback)
                                else      -> MovixLinks.processlinks(brandname, listOf(targeturl), mainUrl, subCallback, callback)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.d("movix", "API error $brandname: ${e.message}")
                    }
                }
            }

            val embedJobs = embedProviders.map { (brandname, targeturl) ->
                async {
                    try {
                        Log.d("movix", "Embed: $brandname -> $targeturl")
                        when (brandname) {
                            "Vidlink"   -> Vidlink().getUrl(targeturl, mainUrl, subCallback, callback)
                            "Peachify"  -> Peachify().getUrl(targeturl, mainUrl, subCallback, callback)
                            "Vidsrc.io" -> VidsrcIo().getUrl(targeturl, mainUrl, subCallback, callback)
                            else        -> loadcustomextractor(brandname, targeturl, mainUrl, subCallback, callback)
                        }
                    } catch (e: Throwable) {
                        Log.d("movix", "Embed error $brandname: ${e.message}")
                    }
                }
            }

            (apiJobs + embedJobs).awaitAll()
        }

        true
    }
}
