package com.Megakino

import android.annotation.SuppressLint
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.app
import com.seoulentertainment.app.utils.AppUtils.toJson
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.M3u8Helper

open class Gxplayer : ExtractorApi() {
    override var name = "Gxplayer"
    override var mainUrl = "https://watch.gxplayer.xyz"
    override val requiresReferer = true

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val json = app.get(url,referer=mainUrl).text.substringAfter("var video = ").substringBefore(";").toJson()
        val objectMapper = jacksonObjectMapper()
        val video: Details = objectMapper.readValue(json)
                M3u8Helper.generateM3u8(
                    this.name,
                    "$mainUrl/m3u8/${video.uid}/${video.md5}/master.txt?s=1&id=${video.id}&cache=${video.status}",
                    referer = "$mainUrl/",
                ).forEach(callback)
    }
}


data class Details(
    val id: String,
    val uid: String,
    val slug: String,
    val title: String,
    val folderid: Any?,
    val quality: String,
    val sources: Any?,
    val type: String,
    val userlinkhost: String,
    val poster: String,
    val subtitles: Any?,
    val added: String,
    val updatedtime: String,
    val status: String,
    val errorcount: String,
    val progressbar: String,
    val progress: String,
    val views: String,
    val md5: String,
)
