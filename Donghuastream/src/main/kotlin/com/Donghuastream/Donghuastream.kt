package com.Donghuastream


import com.lagradost.api.Log
import com.seoulentertainment.app.HomePageList
import com.seoulentertainment.app.HomePageResponse
import com.seoulentertainment.app.LoadResponse
import com.seoulentertainment.app.MainAPI
import com.seoulentertainment.app.MainPageRequest
import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.app
import com.seoulentertainment.app.base64Decode
import com.seoulentertainment.app.fixUrl
import com.seoulentertainment.app.fixUrlNull
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.newEpisode
import com.seoulentertainment.app.newHomePageResponse
import com.seoulentertainment.app.newMovieLoadResponse
import com.seoulentertainment.app.newMovieSearchResponse
import com.seoulentertainment.app.newTvSeriesLoadResponse
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.INFER_TYPE
import com.seoulentertainment.app.utils.getQualityFromName
import com.seoulentertainment.app.utils.httpsify
import com.seoulentertainment.app.utils.loadExtractor
import com.seoulentertainment.app.utils.newExtractorLink
import com.seoulentertainment.app.amap
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class Donghuastream : MainAPI() {
    override var mainUrl              = "https://donghuastream.org"
    override var name                 = "Donghuastream"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=&type=special&sub=&order=update" to "Special Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home     = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx a img")?.getImageAttr())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> this.attr("src")
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/pagg/$i/?s=$query").document

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href=document.selectFirst(".eplister li > a")?.attr("href") ?:""
        var poster = document.select("div.ime > img").attr("data-src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type=document.selectFirst(".spe")?.text().toString()
        val tvtag=if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage= document.selectFirst(".eplister li > a")?.attr("href") ?:""
            val doc= app.get(Eppage).document
            val episodes=doc.select("div.episodelist > ul > li").map { info->
                        val href1 = info.select("a").attr("href")
                        val episode = info.select("a span").text().substringAfter("-").substringBeforeLast("-")
                        val posterr=info.selectFirst("a img")?.attr("data-src") ?:""
                        newEpisode(href1)
                        {
                            this.name=episode.replace(title,"",ignoreCase = true)
                            this.episode=episode.toIntOrNull()
                            this.posterUrl=posterr
                        }
            }
            if (poster.isEmpty())
            {
                poster=document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            if (poster.isEmpty())
            {
                poster=document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).document

        val options = html.select("option[data-index]")

        options.amap { option ->
            val base64 = option.attr("value")
            if (base64.isBlank()) return@amap
            val label = option.text().trim()
            val decodedHtml = try {
                base64Decode(base64)
            } catch (_: Exception) {
                Log.w("Error", "Base64 decode failed: $base64")
                return@amap
            }

            val iframeUrl = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")?.let(::httpsify)
            if (iframeUrl.isNullOrEmpty()) return@amap
            when {
                "vidmoly" in iframeUrl -> {
                    val cleanedUrl = "http:" + iframeUrl.substringAfter("=\"").substringBefore("\"")
                    loadExtractor(cleanedUrl, referer = iframeUrl, subtitleCallback, callback)
                }
                iframeUrl.endsWith(".mp4") -> {
                    callback(
                        newExtractorLink(
                            label,
                            label,
                            url = iframeUrl,
                            INFER_TYPE
                        ) {
                            this.referer = ""
                            this.quality = getQualityFromName(label)
                        }
                    )
                }
                else -> {
                    loadExtractor(iframeUrl, referer = iframeUrl, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
