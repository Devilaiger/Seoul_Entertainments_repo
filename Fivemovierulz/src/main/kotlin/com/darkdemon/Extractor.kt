package com.darkdemon
import com.seoulentertainment.app.extractors.StreamWishExtractor
import com.seoulentertainment.app.extractors.VidhideExtractor

class Filelion : VidhideExtractor() {
    override var mainUrl = "https://vidhidepro.com"
}


class mivalyo : VidhideExtractor() {
    override var name = "Mivalyo"
    override var mainUrl = "https://mivalyo.com"
}

class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}