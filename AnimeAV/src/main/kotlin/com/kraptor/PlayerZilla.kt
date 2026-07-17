package com.kraptor

import android.annotation.SuppressLint
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.ExtractorLinkType
import com.seoulentertainment.app.utils.Qualities
import com.seoulentertainment.app.utils.newExtractorLink

class PlayerZilla : ExtractorApi() {
    override var name = "PlayerZilla"
    override var mainUrl = "https://player.zilla-networks.com"
    override val requiresReferer = false

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val video = "$mainUrl/m3u8/${url.substringAfterLast("/")}"
            callback.invoke(
                newExtractorLink(
                this.name,
                this.name,
                url = video,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P1080.value
            }
        )
    }
}