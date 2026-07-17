package com.Animeav1

import com.seoulentertainment.app.extractors.VidStack
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.ExtractorLinkType
import com.seoulentertainment.app.utils.Qualities
import com.seoulentertainment.app.utils.newExtractorLink

open class Zilla : ExtractorApi() {
    override var name = "HLS"
    override var mainUrl = "https://player.zilla-networks.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val mp4 = "$mainUrl/m3u8/${url.substringAfterLast("/")}"
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = mp4,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.P1080.value
                }
            )
    }
}

class Animeav1upn : VidStack() {
    override var mainUrl = "https://animeav1.uns.bio"
}