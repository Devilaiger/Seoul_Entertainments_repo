package com.phisher98

import com.lagradost.api.Log
import com.seoulentertainment.app.ErrorLoadingException
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.app
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.utils.AppUtils.parseJson
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.loadExtractor
import com.seoulentertainment.app.amap

class HindiSubAnime : AnimeDekhoProvider() {
    override var mainUrl = "https://hindisubanime.co"
    override var name = "HindiSubAnime"
    override val hasMainPage = true
    override var lang = "hi"

    override val mainPage =
        mainPageOf(
            "/category/shounen/" to "Shounen",
            "/category/action/" to "Action",
            "/category/fantasy/" to "Fantasy",
            "/serie/" to "Series",
        )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val media = parseJson<Media>(data)
        val body = app.get(media.url).document.selectFirst("body")?.attr("class") ?: return false
        val term = Regex("""(?:term|postid)-(\d+)""").find(body)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("no id found")
        (0..4).toList().amap { i ->
            val link = app.get("$mainUrl/?trdekho=$i&trid=$term&trtype=${media.mediaType}")
                .document.selectFirst("iframe")?.attr("src")
                ?: return@amap
            Log.d("Phisher", link)
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}