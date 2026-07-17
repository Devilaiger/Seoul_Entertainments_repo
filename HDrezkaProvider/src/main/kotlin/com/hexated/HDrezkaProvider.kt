package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.*
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

class HDrezkaProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
        @Volatile private var lastBrowserOpenMs = 0L
        private const val BROWSER_DEBOUNCE_MS = 10_000L
    }

    override var mainUrl = "https://rezka.ag"
    override var name = "HDrezka"
    override val hasMainPage = true
    override var lang = "ru"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/films/?filter=watching" to "фильмы",
        "$mainUrl/series/?filter=watching" to "сериалы",
        "$mainUrl/cartoons/?filter=watching" to "мультфильмы",
        "$mainUrl/animation/?filter=watching" to "аниме",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data.split("?")
        val home = app.get("${url.first()}page/$page/?${url.last()}").document.select(
            "div.b-content__inline_items div.b-content__inline_item"
        ).map {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val rawTitle = this.selectFirst("div.b-content__inline_item-link > div")?.text()?.trim()
        val title = if (!rawTitle.isNullOrEmpty() && rawTitle.contains(",")) {
            val originalTitle = rawTitle.substringBefore(",").trim()
            if (originalTitle.toIntOrNull() != null) {
                this.selectFirst("div.b-content__inline_item-link > a")?.text()?.trim().toString()
            } else {
                originalTitle
            }
        } else {
            this.selectFirst("div.b-content__inline_item-link > a")?.text()?.trim().toString()
        }
        val href = this.selectFirst("a")?.attr("href").toString()
        val posterUrl = this.select("img").attr("src")
        val type = if (this.select("span.info").isNotEmpty()) TvType.TvSeries else TvType.Movie
        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            val episode =
                this.select("span.info").text().substringAfter(",").replace(Regex("[^0-9]"), "")
                    .toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addDubStatus(
                    dubExist = true,
                    dubEpisodes = episode,
                    subExist = true,
                    subEpisodes = episode
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        
        val link = "$mainUrl/search/?do=search&subaction=search&q=$query"
        val document = app.get(link).document

        return document.select("div.b-content__inline_items div.b-content__inline_item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        
        val document = app.get(url).document
        android.util.Log.d("HDrezka", "Url: $url")
        android.util.Log.d("HDrezka", "Doc length: ${document.toString().length}")
        android.util.Log.d("HDrezka", "Doc head: ${document.head().toString().take(300)}")
        android.util.Log.d("HDrezka", "Doc body: ${document.body()?.html()?.take(1000)}")

        val id = url.split("/").last().split("-").first()
        val title = (document.selectFirst("div.b-post__origtitle")?.text()?.trim()
            ?: document.selectFirst("div.b-post__title h1")?.text()?.trim()).toString()
        val poster = fixUrlNull(document.selectFirst("div.b-sidecover img")?.attr("src"))
        val tags =
            document.select("table.b-post__info tr:contains(Жанр) span[itemprop=genre]")
                .map { it.text() }.toMutableList()
        tags.add(0, "Russian Provider")
        val year = document.selectFirst("table.b-post__info tr:contains(Год) a")?.text()?.toIntOrNull()
            ?: document.selectFirst("table.b-post__info tr:contains(Дата выхода) a")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
        val tvType = if (document.select("#simple-episodes-tabs")
                .isNullOrEmpty()
        ) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("div.b-post__description_text")?.text()?.trim()
        val englishDesc = translateToEnglish(description)
        val combinedDesc = if (englishDesc != null && englishDesc != description) {
            "$englishDesc\n\n────────────────────\n🇷🇺 [Russian / Русский]\n$description"
        } else {
            description
        }
        val trailer = app.post(
            "$mainUrl/engine/ajax/gettrailervideo.php",
            data = mapOf("id" to id),
            referer = url
        ).parsedSafe<Trailer>()?.code.let {
            Jsoup.parse(it.toString()).select("iframe").attr("src")
        }
        val ratingText =
            document.selectFirst("table.b-post__info tr:contains(Рейтинги) span.bold")?.text()
                ?: document.selectFirst("table.b-post__info tr:nth-child(1) span.bold")?.text()
        val score = ratingText?.toDoubleOrNull()?.let { Score.from10(it) }
        val actors =
            document.select("table.b-post__info tr:contains(В ролях) span.item").mapNotNull {
                Actor(
                    it.selectFirst("span[itemprop=name]")?.text() ?: return@mapNotNull null,
                    it.selectFirst("span[itemprop=actor]")?.attr("data-photo")
                )
            }

        val recommendations = document.select("div.b-sidelist div.b-content__inline_item").map {
            it.toSearchResult()
        }

        val data = HashMap<String, Any>()
        val server = ArrayList<Map<String, String>>()

        data["id"] = id
        data["favs"] = document.selectFirst("input#ctrl_favs")?.attr("value").toString()
        data["ref"] = url

        return if (tvType == TvType.TvSeries) {
            val translators = document.select("ul#translators-list li")
            if (translators.isNotEmpty()) {
                translators.map { res ->
                    server.add(
                        mapOf(
                            "translator_name" to res.text(),
                            "translator_id" to res.attr("data-translator_id"),
                        )
                    )
                }
            } else {
                // Extracts the default translator_id from the init script if translation list is missing
                document.select("script").map { script ->
                    val match = Regex("initCDNSeriesEvents\\(\\d+,\\s*(\\d+)").find(script.data())
                    if (match != null) {
                        server.add(
                            mapOf(
                                "translator_name" to "HDrezka",
                                "translator_id" to match.groupValues[1]
                            )
                        )
                    }
                }
            }
            val episodes = document.select(
                    "#simple-episodes-tabs .b-simple_episode__item"
                ).map { ep ->

                    val season = ep.attr("data-season_id").toIntOrNull()
                    val episode = ep.attr("data-episode_id").toIntOrNull()

                    val name = ep.selectFirst(".b-simple_episode__title")
                        ?.text()
                        ?.ifBlank { "Episode $episode" }
                        ?: "Episode $episode"

                    data["season"] = "$season"
                    data["episode"] = "$episode"
                    data["server"] = server
                    data["action"] = "get_stream"

                    newEpisode(data.toJson()) {
                        this.name = name
                        this.season = season
                        this.episode = episode
                    }
                }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = combinedDesc
                this.tags = tags
                this.score = score
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            document.select("ul#translators-list li").map { res ->
                server.add(
                    mapOf(
                        "translator_name" to res.text(),
                        "translator_id" to res.attr("data-translator_id"),
                        "camrip" to res.attr("data-camrip"),
                        "ads" to res.attr("data-ads"),
                        "director" to res.attr("data-director")
                    )
                )
            }

            data["server"] = server
            data["action"] = "get_movie"

            newMovieLoadResponse(title, url, TvType.Movie, data.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = combinedDesc
                this.tags = tags
                this.score = score
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private suspend fun translateToEnglish(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return try {
            val response = app.get(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ru&tl=en&dt=t&q=${java.net.URLEncoder.encode(text, "UTF-8")}"
            ).text
            val parsed = tryParseJson<List<Any>>(response)
            val segments = parsed?.getOrNull(0) as? List<*>
            segments?.mapNotNull { segment ->
                val innerList = segment as? List<*>
                innerList?.getOrNull(0) as? String
            }?.joinToString("")
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptStreamUrl(data: String): String {
        if (data.startsWith("[")) return data

        fun getTrash(arr: List<String>, item: Int): List<String> {
            val trash = ArrayList<List<String>>()
            for (i in 1..item) {
                trash.add(arr)
            }
            return trash.reduce { acc, list ->
                val temp = ArrayList<String>()
                acc.forEach { ac ->
                    list.forEach { li ->
                        temp.add(ac.plus(li))
                    }
                }
                return@reduce temp
            }
        }

        val trashList = listOf("@", "#", "!", "^", "$")
        val trashSet = getTrash(trashList, 2) + getTrash(trashList, 3)
        val cleaned = data.replace(Regex("^#[a-zA-Z0-9]+"), "")
        var trashString = cleaned.split("//_//").joinToString("")

        trashSet.forEach {
            val temp = base64Encode(it.toByteArray())
            trashString = trashString.replace(temp, "")
        }

        return base64Decode(trashString)
    }

    private suspend fun cleanCallback(
        source: String,
        url: String,
        quality: String,
        isM3u8: Boolean,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        sourceCallback.invoke(
            newExtractorLink(
                source,
                source,
                url,
                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = getQuality(quality)
                this.headers = mapOf(
                    "Origin" to mainUrl
                )
            }
        )
    }

    private fun getLanguage(str: String): String {
        return when (str) {
            "Русский" -> "Russian"
            "Українська" -> "Ukrainian"
            else -> str
        }
    }

    private fun getTranslatorName(str: String): String {
        val clean = str.trim()
        return when {
            clean.equals("Дубляж", ignoreCase = true) -> "Dubbing"
            clean.contains("Профессиональный многоголосый", ignoreCase = true) -> "Professional Multivoice"
            clean.contains("Любительский многоголосый", ignoreCase = true) -> "Amateur Multivoice"
            clean.contains("Профессиональный двухголосый", ignoreCase = true) -> "Professional Voiceover (2-voiced)"
            clean.contains("Любительский двухголосый", ignoreCase = true) -> "Amateur Voiceover (2-voiced)"
            clean.contains("Профессиональный одноголосый", ignoreCase = true) -> "Professional Voiceover (1-voiced)"
            clean.contains("Любительский одноголосый", ignoreCase = true) -> "Amateur Voiceover (1-voiced)"
            clean.contains("Оригинальная дорожка", ignoreCase = true) -> "Original Audio"
            clean.contains("Субтитры", ignoreCase = true) -> "Subtitles"
            else -> clean
        }
    }

    private fun getQuality(str: String): Int {
        val cleanStr = str.lowercase(Locale.ROOT)
        return when {
            cleanStr.contains("360p") -> Qualities.P360.value
            cleanStr.contains("480p") -> Qualities.P480.value
            cleanStr.contains("720p") -> Qualities.P720.value
            cleanStr.contains("1080p") -> Qualities.P1080.value
            cleanStr.contains("1440p") -> Qualities.P1440.value
            cleanStr.contains("2160p") -> Qualities.P2160.value
            else -> getQualityFromName(str)
        }
    }

    private suspend fun invokeSources(
        source: String,
        url: String,
        subtitle: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val addedQualities = mutableSetOf<Int>()
        decryptStreamUrl(url).split(",").map { links ->
            val quality =
                Regex("\\[([^]]+)]").find(links)?.groupValues?.getOrNull(1)
                    ?.trim() ?: return@map null
            val qualityInt = getQuality(quality)
            if (addedQualities.contains(qualityInt)) return@map null
            addedQualities.add(qualityInt)

            links.replace("[$quality]", "").split(" or ")
                .map {
                    val link = it.trim()
                    val type = if(link.contains(".m3u8")) "(Main)" else "(Backup)"
                    cleanCallback(
                        "$source $type",
                        link,
                        quality,
                        link.contains(".m3u8"),
                        sourceCallback,
                    )
                }
        }

        subtitle.split(",").map { sub ->
            val language =
                Regex("\\[(.*)]").find(sub)?.groupValues?.getOrNull(1) ?: return@map null
            val link = sub.replace("[$language]", "").trim()
            subCallback.invoke(
                newSubtitleFile(
                    getLanguage(language),
                    link
                )
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        tryParseJson<Data>(data)?.let { res ->
            if (res.server?.isEmpty() == true) {
                val document = app.get(res.ref ?: return@let).document
                document.select("script").map { script ->
                    if (script.data().contains("sof.tv.initCDNMoviesEvents(")) {
                        val dataJson =
                            script.data().substringAfter("false, {").substringBefore("});")
                        tryParseJson<LocalSources>("{$dataJson}")?.let { source ->
                            invokeSources(
                                this.name,
                                source.streams,
                                source.subtitle.toString(),
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            } else {
                res.server?.take(4)?.map { server ->
                    app.post(
                        url = "$mainUrl/ajax/get_cdn_series/?t=${Date().time}",
                        data = mapOf(
                            "id" to res.id,
                            "translator_id" to server.translator_id,
                            "favs" to res.favs,
                            "is_camrip" to server.camrip,
                            "is_ads" to server.ads,
                            "is_director" to server.director,
                            "season" to res.season,
                            "episode" to res.episode,
                            "action" to res.action,
                        ).filterValues { it != null }.mapValues { it.value as String },
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Accept" to "application/json, text/javascript, */*; q=0.01",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        ),
                        referer = res.ref
                    ).parsedSafe<Sources>()?.let { source ->
                        invokeSources(
                            getTranslatorName(server.translator_name.toString()),
                            source.url,
                            source.subtitle.toString(),
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }

        return true
    }

    data class LocalSources(
        @JsonProperty("streams") val streams: String,
        @JsonProperty("subtitle") val subtitle: Any?,
    )

    data class Sources(
        @JsonProperty("url") val url: String,
        @JsonProperty("subtitle") val subtitle: Any?,
    )

    data class Server(
        @JsonProperty("translator_name") val translator_name: String?,
        @JsonProperty("translator_id") val translator_id: String?,
        @JsonProperty("camrip") val camrip: String?,
        @JsonProperty("ads") val ads: String?,
        @JsonProperty("director") val director: String?,
    )

    data class Data(
        @JsonProperty("id") val id: String?,
        @JsonProperty("favs") val favs: String?,
        @JsonProperty("server") val server: List<Server>?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("episode") val episode: String?,
        @JsonProperty("action") val action: String?,
        @JsonProperty("ref") val ref: String?,
    )

    data class Trailer(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("code") val code: String?,
    )




    private fun openInExternalBrowser(url: String) {
        if (isLayout(TV)) return
        val ctx = context ?: return
        val now = System.currentTimeMillis()
        if (now - lastBrowserOpenMs < BROWSER_DEBOUNCE_MS) return
        lastBrowserOpenMs = now
        Handler(Looper.getMainLooper()).post {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) { }
        }
    }
}