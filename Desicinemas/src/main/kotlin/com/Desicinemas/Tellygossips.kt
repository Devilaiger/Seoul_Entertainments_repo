package com.Desicinemas

//import com.lagradost.api.Log
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.ExtractorLinkType
import com.seoulentertainment.app.utils.Qualities
import com.seoulentertainment.app.utils.newExtractorLink

class Tellygossips(private val source:String) : ExtractorApi() {
    override val mainUrl = "https://flow.tellygossips.net"
    override val name = "Tellygossips"
    override val requiresReferer = false
    private val referer = "http://tellygossips.net/"
    private val configRegex = "var config = ([\\s\\S]*?);".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = this.referer).document
        val configStr = doc.select("script")
            .map { it.data() }
            .firstOrNull { it.contains("var config = ") }
            ?.let { configRegex.find(it.trim())?.groupValues?.get(1) } ?: return
        val config = tryParseJson<Config>(configStr) ?: return
        for (link in config.sources) {
            callback(
                newExtractorLink(
                    "$name $source",
                    name,
                    url = link.file ?: link.src ?: continue,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        }
    }

    data class Config(
        val sources: List<VideoLink>,
    )

    data class VideoLink(
        val file: String?,
        val src: String?,
        val label: String,
        val type: String,
    )

}