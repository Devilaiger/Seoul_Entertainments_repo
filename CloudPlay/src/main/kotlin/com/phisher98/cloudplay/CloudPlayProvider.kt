package com.phisher98.cloudplay


import com.seoulentertainment.app.HomePageList
import com.seoulentertainment.app.HomePageResponse
import com.seoulentertainment.app.LoadResponse
import com.seoulentertainment.app.MainAPI
import com.seoulentertainment.app.MainPageRequest
import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.amap
import com.seoulentertainment.app.app
import com.seoulentertainment.app.base64Decode
import com.seoulentertainment.app.base64DecodeArray
import com.seoulentertainment.app.newHomePageResponse
import com.seoulentertainment.app.newLiveSearchResponse
import com.seoulentertainment.app.newLiveStreamLoadResponse
import com.seoulentertainment.app.utils.AppUtils.parseJson
import com.seoulentertainment.app.utils.AppUtils.toJson
import com.seoulentertainment.app.utils.CLEARKEY_UUID
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.ExtractorLinkType
import com.seoulentertainment.app.utils.INFER_TYPE
import com.seoulentertainment.app.utils.newDrmExtractorLink
import com.seoulentertainment.app.utils.newExtractorLink
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CloudPlay : MainAPI() {
    override var lang = "en"
    override var mainUrl: String = base64Decode("aHR0cHM6Ly9ob3N0LmNsb3VkcGxheS5tZQ==")
    override var name = "CloudPlay"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    private val apiHeaders = mapOf(
        "Connection" to "Keep-Alive",
        "User-Agent" to "okhttp/4.12.0",
        "X-Package" to base64Decode("Y29tLmNsb3VkcGxheS5hcHA=")
    )

    private fun generateSign(ts: Long): String {
        val key = base64Decode("amlvdHZwbHVz")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(ts.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun mainPhpUrl(): String {
        val ts = System.currentTimeMillis() / 1000L
        val sign = generateSign(ts)
        return "$mainUrl/main.php?ts=$ts&sign=$sign"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val req = app.get(mainPhpUrl(), headers = apiHeaders)
        val res = req.parsedSafe<CloudPlayResponse>()
            ?: throw Error("Failed to parse main.php. Text: ${req.text}")

        val decryptedJson = decryptPayload(res.payload, res.iv, res.tag ?: "")
        val streams = parseJson<CloudPlayStreams>(decryptedJson).streams

        val homePageLists = mutableListOf<HomePageList>()
        streams.amap { stream ->
            val sections = fetchHomeSections(stream.name ?: "Unknown", stream.url, stream.logo)
            homePageLists.addAll(sections)
        }

        return newHomePageResponse(homePageLists)
    }

/**
     * Fetches a URL and returns one or more HomePageLists.
     * If the URL contains sub-streams (nested CloudPlayStream list), each sub-stream
     * becomes its own separate HomePageList instead of being merged into a single one.
     * If the URL returns direct channels (JSON list or M3U), a single HomePageList is returned.
     */