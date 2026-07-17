package com.Animekhor

import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.app
import com.seoulentertainment.app.base64Decode
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.loadExtractor
import com.seoulentertainment.app.amap


class Donghuaword  : Animekhor() {
    override var mainUrl              = "https://donghuaworld.com"
    override var name                 = "Donghuaword"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Anime)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("div.server-item a").amap {
            val base64=it.attr("data-hash")
            val decodedUrl = base64Decode(base64)
            val regex = Regex("""src=["']([^"']+)["']""",RegexOption.IGNORE_CASE)
            val matchResult = regex.find(decodedUrl)
            val url = matchResult?.groups?.get(1)?.value ?: "Not found"
            loadExtractor(url, referer = mainUrl, subtitleCallback, callback)

        }
        return true
    }
}