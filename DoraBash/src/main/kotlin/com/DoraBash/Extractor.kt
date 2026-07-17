package com.DoraBash


import com.seoulentertainment.app.app
import com.seoulentertainment.app.extractors.Filesim
import com.seoulentertainment.app.extractors.StreamSB
import com.seoulentertainment.app.extractors.StreamWishExtractor
import com.seoulentertainment.app.utils.ExtractorApi
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.INFER_TYPE
import com.seoulentertainment.app.utils.JsUnpacker
import com.seoulentertainment.app.utils.Qualities
import com.seoulentertainment.app.utils.newExtractorLink

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
    }
}

class wishfast : StreamWishExtractor() {
    override var mainUrl = "https://wishfast.top"
    override var name = "StreamWish"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

class FileMoonIN : Filesim() {
    override val mainUrl = "https://filemoon.in"
    override val name = "FileMoonSx"
}