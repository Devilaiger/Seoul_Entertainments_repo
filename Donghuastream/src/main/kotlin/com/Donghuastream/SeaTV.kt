package com.Donghuastream

import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.amap
import com.seoulentertainment.app.app
import com.seoulentertainment.app.base64Decode
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.INFER_TYPE
import com.seoulentertainment.app.utils.getQualityFromName
import com.seoulentertainment.app.utils.httpsify
import com.seoulentertainment.app.utils.loadExtractor
import com.seoulentertainment.app.utils.newExtractorLink
import org.jsoup.Jsoup


open class SeaTV : Donghuastream() {
    override var mainUrl              = "https://seatv-24.xyz"
    override var name                 = "SeaTV"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=upcoming&type=&sub=&order=" to "Upcoming",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value").takeIf { it.isNotEmpty() }
            val doc = base64?.let { base64Decode(it).let(Jsoup::parse) }
            val iframeUrl = doc?.select("iframe")?.attr("src")?.let(::httpsify)
            val metaUrl = doc?.select("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
            val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()
            if (url.isNotEmpty()) {
                when {
                    url.contains("vidmoly") -> {
                        val newUrl = url.substringAfter("=\"").substringBefore("\"")
                        val link = "http:$newUrl"
                        loadExtractor(link, referer = url, subtitleCallback, callback)
                    }
                    url.endsWith("mp4") -> {
                        callback.invoke(
                            newExtractorLink(
                                "All Sub Player",
                                "All Sub Player",
                                url = url,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else -> {
                        loadExtractor(url, referer = url, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }
}