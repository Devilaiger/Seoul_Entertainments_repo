package com.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.app
import com.seoulentertainment.app.newSubtitleFile
import com.seoulentertainment.app.utils.AppUtils
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.M3u8Helper.Companion.generateM3u8
import com.seoulentertainment.app.utils.getAndUnpack

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = referer,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource.replace(".txt",".m3u8")

        generateM3u8(name,
            m3uLink,
            mainUrl,
        ).forEach(callback)

        document.select("script").forEach { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(subtitle.label ?: ""),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    data class ResponseSource(
        @param:JsonProperty("hls") val hls: Boolean,
        @param:JsonProperty("videoSource") val videoSource: String,
        @param:JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @param:JsonProperty("kind") val kind: String?,
        @param:JsonProperty("file") val file: String,
        @param:JsonProperty("label") val label: String?,
    )
    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str
                .contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }
}

class Majorplay : ExtractorApi() {
    override var name = "Majorplay"
    override var mainUrl = "https://*.majorplay.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = mainUrl).document
        val m3uLink = document.select("source").attr("src")
        Log.d(name, m3uLink)
        generateM3u8(name, m3uLink, mainUrl).forEach(callback)

        val scripts = document.selectFirst("script:containsData(subtitles)")?.data() ?: return

        val subRegex = Regex("""\\"label\\":\\"([^\\"]*?)\\"[^}]*?\\"path\\":\\"([^\\"]*?)\\"""")

        subRegex.findAll(scripts).forEach { match ->
            val label = match.groupValues[1]
            var vttUrl = match.groupValues[2]

            if (!vttUrl.startsWith("http")) {
                vttUrl = mainUrl.trimEnd('/') + vttUrl
            }

            subtitleCallback.invoke(
                newSubtitleFile
                    (label, vttUrl)
            )
        }
    }
}