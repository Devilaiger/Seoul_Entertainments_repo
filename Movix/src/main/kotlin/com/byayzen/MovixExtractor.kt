package com.byayzen

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.*
import com.lagradost.cloudstream3.network.webscrape.*
import kotlinx.coroutines.withTimeoutOrNull
import com.lagradost.cloudstream3.extractors.StreamWishExtractor

import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.LuluStream
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class Vidaraa : ExtractorApi() {
    override var name = "Vidaraa"
    override var mainUrl = "https://vidaraa.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val filecode = url.substringAfterLast("/")
        val apiurl = "$mainUrl/api/stream"
        val payload = """{"filecode":"$filecode","device":"web"}"""

        Log.d("Vidaraa", "URL: $apiurl")
        val response = app.post(
            apiurl,
            requestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
            headers = mapOf(
                "Referer" to url,
                "Origin" to mainUrl,
                "User-Agent" to USER_AGENT
            )
        )
        
        val resText = response.text
        Log.d("Vidaraa", "Res: $resText")
        val streamurl = JSONObject(resText).optString("streaming_url")
        
        if (streamurl.isNotBlank()) {
            val check = app.get(streamurl, referer = "$mainUrl/", timeout = 5)
            Log.d("Vidaraa", "Code: ${check.code}")
            if (check.code == 200) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = streamurl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                    }
                )
            }
        }
    }
}

class Ralphy : Voe() { override var mainUrl = "https://ralphysuccessfull.org" }
class Bryantenunder : Voe() { override var mainUrl = "https://bryantenunder.com" }
class Pamelachangemission : Voe() { override var mainUrl = "https://pamelachangemission.com" }

open class Uqload : ExtractorApi() {
    override var name = "Uqload"
    override var mainUrl = "https://uqload.is"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to "$mainUrl/"
            )
            val embedResponse = app.get(url, headers = headers, timeout = 15)
            val responseText = embedResponse.text

            val videoUrl = Regex("""sources:\s*\["([^"]+)"\]""").find(responseText)?.groupValues?.get(1)
                ?: Regex("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""").find(responseText)?.groupValues?.get(1)
                ?: Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(responseText)?.groupValues?.get(1)
                ?: getAndUnpack(responseText).let { unpacked ->
                    Regex("""(https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)""").find(unpacked)?.groupValues?.get(1)
                }

            if (videoUrl != null && videoUrl.isNotBlank()) {
                val isM3u8 = videoUrl.contains(".m3u8")
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = if (isM3u8) Qualities.Unknown.value else 1080
                        this.headers = headers
                    }
                )
            }
        } catch (e: Exception) {
            Log.d(name, "Uqload error: ${e.message}")
        }
    }
}

class UqloadIo : Uqload() {
    override var mainUrl = "https://uqload.io"
}

class Uqloadcx : Uqload() {
    override var mainUrl = "https://uqload.cx"
}

class Uqloadto : Uqload() {
    override var mainUrl = "https://uqload.to"
}

open class DoodStream : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://myvidplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("doply.net", "myvidplay.com")
        val response = app.get(
            embedUrl,
            referer = mainUrl,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
        ).text

        val md5Regex = Regex("/pass_md5/([^/]*)/([^/']*)")
        val md5Match = md5Regex.find(response)
        val md5Path = md5Match?.value.toString()
        val expiry = md5Match?.groupValues?.getOrNull(1) ?: ""
        val token = md5Match?.groupValues?.getOrNull(2) ?: ""
        val md5Url = mainUrl + md5Path

        val md5Response = app.get(
            md5Url,
            referer = embedUrl,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
        ).text

        val baseLink = md5Response.trim()
        val directLink = if (token.isNotEmpty() && expiry.isNotEmpty()) {
            "$baseLink?token=$token&expiry=${expiry}000"
        } else {
            baseLink
        }

        callback.invoke(
            newExtractorLink(
                source = this.name, name = this.name, url = directLink, type = INFER_TYPE
            ) {
                this.referer = "https://myvidplay.com"
                this.quality = Qualities.Unknown.value
                this.headers =
                    mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")
            })
    }
}

class Vide0Net : DoodStream() {
    override var mainUrl = "https://vide0.net"
}

class DoodDoply : DoodStream() {
    override var mainUrl = "https://doply.net"
}

class Playmogo : DoodStream() {
    override var mainUrl = "https://playmogo.com"
}

class GhBrisk : StreamWishExtractor() {
    override var mainUrl = "https://ghbrisk.com"
    override var name = "StreamWish"
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val embedUrl = getEmbedUrl(url)
        Log.d("vidhide", embedUrl)

        try {
            val response = app.get(embedUrl, referer = referer)
            Log.d("vidhide", "${response.code}|${response.text.length}")

            val responseText = response.text
            val packed = getPacked(responseText)
            val script = if (!packed.isNullOrEmpty()) {
                var result = getAndUnpack(responseText)
                if (result.contains("var links")) {
                    result = result.substringAfter("var links")
                }
                result
            } else {
                response.document.selectFirst("script:containsData(sources:)")?.data()
            }

            if (script == null) {
                return
            }

            Log.d("vidhide", script.length.toString())
            val regex = Regex(":\\s*\"(.*?m3u8.*?)\"")
            val matches = regex.findAll(script).toList()
            Log.d("vidhide", matches.size.toString())

            matches.forEach { m3u8Match ->
                val m3u8Url = fixUrl(m3u8Match.groupValues[1])
                Log.d("vidhide", m3u8Url)
                generateM3u8(
                    name,
                    m3u8Url,
                    referer = "$mainUrl/",
                    headers = headers
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.d("Vidhide", e.message ?: "null")
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}

class RyderJet : VidHidePro() { override var mainUrl = "https://ryderjet.com" }
class VtbeTo : VidHidePro() { override var mainUrl = "https://vtbe.to" }
class SaveFiles : VidHidePro() { override var mainUrl = "https://savefiles.com" }
class DhcPlay : VidHidePro() { override var mainUrl = "https://dhcplay.com" }
class FileLionsLive : VidHidePro() { override var mainUrl = "https://filelions.live" }
class FileLionsOnline : VidHidePro() { override var mainUrl = "https://filelions.online" }
class FileLionsTo : VidHidePro() { override var mainUrl = "https://filelions.to" }
class KinogerBe : VidHidePro() { override val mainUrl = "https://kinoger.be" }
class VidHideHub : VidHidePro() { override val mainUrl = "https://vidhidehub.com" }
class VidHideVip : VidHidePro() { override val mainUrl = "https://vidhidevip.com" }
class VidHidePre : VidHidePro() { override val mainUrl = "https://vidhidepre.com" }
class SmoothPre : VidHidePro() { override var mainUrl = "https://smoothpre.com" }
class DhtPre : VidHidePro() { override var mainUrl = "https://dhtpre.com" }
class PeytonePre : VidHidePro() { override var mainUrl = "https://peytonepre.com" }
class MovearnPre : VidHidePro() { override var mainUrl = "https://movearnpre.com" }
class Dintezuvio : VidHidePro() { override var mainUrl = "https://dintezuvio.com" }

class Morencius : VidHidePro() { override var mainUrl = "https://morencius.com" }

open class Vidzy : ExtractorApi() {
    override val name = "Vidzy"
    override val mainUrl = "https://vidzy.live"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if (result.contains("var links")) {
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Log.d("Vidzy", "$script")
        Regex("""src\s*:\s*"([^"]+m3u8[^"]*)"""").findAll(script).forEach { m3u8Match ->
            val m3u8Url = fixUrl(m3u8Match.groupValues[1])
            Log.d("VidzySon", "M3U8: $m3u8Url")

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                    this.headers = mapOf(
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT
                    )
                }
            )
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            url.contains("/embed-") -> url
            else -> url.replace("/f/", "/v/")
        }
    }
}

open class VeevToExtractor : ExtractorApi() {

    override val name = "VeevTo"
    override val mainUrl = "https://veev.to"
    override val requiresReferer = false

    private fun jsInt(x: String): Int = x.toIntOrNull() ?: 0

    private fun veevDecode(encoded: String): String {
        return try {
            val result = mutableListOf<String>()
            val lut = mutableMapOf<Int, String>()
            var n = 256
            var c = encoded[0].toString()
            result.add(c)

            for (char in encoded.substring(1)) {
                val code = char.code
                val nc = if (code < 256) char.toString() else lut[code] ?: (c + c[0])
                result.add(nc)
                lut[n] = c + nc[0]
                n++
                c = nc
            }
            result.joinToString("")
        } catch (e: Exception) {
            encoded
        }
    }

    private fun buildArray(encoded: String): List<List<Int>> {
        val d = mutableListOf<List<Int>>()
        val chars = encoded.toCharArray().toMutableList()
        if (chars.isEmpty()) return d

        var count = jsInt(chars.removeAt(0).toString())
        while (count > 0) {
            val currentArray = mutableListOf<Int>()
            for (i in 0 until count) {
                if (chars.isEmpty()) break
                currentArray.add(0, jsInt(chars.removeAt(0).toString()))
            }
            d.add(currentArray)
            if (chars.isEmpty()) break
            count = jsInt(chars.removeAt(0).toString())
        }
        return d
    }

    private fun hexToString(hex: String): String {
        val cleanHex = hex.trim()
        val paddedHex = if (cleanHex.length % 2 != 0) "0$cleanHex" else cleanHex
        val bytes = paddedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun decodeUrl(encoded: String, tArray: List<Int>): String {
        var ds = encoded
        for (t in tArray) {
            if (t == 1) ds = ds.reversed()
            ds = hexToString(ds).replace("dXRmOA==", "")
        }
        return ds
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val initialResponse = app.get(url, referer = "$mainUrl/")
            val pageHtml = initialResponse.text
            val mediaId = initialResponse.url.split("/").lastOrNull { it.isNotEmpty() } ?: url.split("/").lastOrNull { it.isNotEmpty() } ?: ""

            val regex = Regex("""[\.\s'](?:fc|_vvto\[[^\]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
            val encodedStrings = regex.findAll(pageHtml).map { it.groupValues[1] }.toList()

            var ch: String? = null
            for (f in encodedStrings.reversed()) {
                val decoded = veevDecode(f)
                if (decoded != f) {
                    ch = decoded
                    break
                }
            }
            if (ch == null) return

            val tArrays = buildArray(ch)
            if (tArrays.isEmpty()) return

            val apiUrl = "$mainUrl/dl?op=player_api&cmd=gi&file_code=${URLEncoder.encode(mediaId, "UTF-8")}&ch=${URLEncoder.encode(ch, "UTF-8")}&ie=1"
            val jsonResponse = JSONObject(app.get(apiUrl, referer = url).text)

            if (jsonResponse.optString("status") != "success") return
            val fileObj = jsonResponse.optJSONObject("file") ?: return
            if (fileObj.optString("file_status") != "OK") return

            val dvArray = fileObj.optJSONArray("dv") ?: return
            val tArray = tArrays[0]

            for (i in 0 until dvArray.length()) {
                val source = dvArray.getJSONObject(i)
                val encodedUrl = source.optString("s")
                if (encodedUrl.isNotEmpty()) {
                    val finalUrl = decodeUrl(veevDecode(encodedUrl), tArray)
                    if (finalUrl.startsWith("http")) {
                        Log.d(name, finalUrl)
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = finalUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "$mainUrl/"
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(name, "${e.message}")
        }
    }
}


class GoodStream : ExtractorApi() {
    override var name = "GoodStream"
    override var mainUrl = "https://goodstream.one"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val code = url.substringAfterLast("/")
        val post = "$mainUrl/dl"
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0"
        val lang = "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
        val res = app.post(
            post,
            data = mapOf(
                "op" to "embed",
                "file_code" to code,
                "auto" to "1",
                "referer" to ""
            ),
            headers = mapOf(
                "Referer" to url,
                "User-Agent" to ua,
                "Accept-Language" to lang
            )
        )

        val src = Regex("""file\s*:\s*"(.*?)"""").find(res.text)?.groupValues?.get(1) ?: return
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = src,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "User-Agent" to ua,
                    "Accept-Language" to lang,
                )
            }
        )
    }
}






class SendvidExtractor : ExtractorApi() {
    override val name = "Sendvid"
    override val mainUrl = "https://sendvid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text

        val videoUrl = Regex("""property="og:video"\s+content="(.*?)"""").find(response)?.groupValues?.get(1)
            ?: Regex("""<source\s+src="(.*?)"\s+type="video/mp4"""").find(response)?.groupValues?.get(1)
            ?: Regex("""var\s+video_source\s+=\s+"(.*?)"""").find(response)?.groupValues?.get(1)

        videoUrl?.let { link ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = link,
                    type = ExtractorLinkType.VIDEO
                ) {
                    headers = mutableMapOf("Referer" to url)
                    quality = getQualityFromName(link)
                }
            )
        }
    }
}




class Coflix : VidStack() { override var mainUrl = "https://coflix.upn.one" }

class Serix : VidStack() { override var mainUrl = "https://serix.upns.live" }

class Flemmix : VidStack() { override var mainUrl = "https://flemmix.upns.pro" }

class Embedseek : VidStack() { override var mainUrl = "https://movix1.embedseek.com" }

class Dismoiceline : VidStack() { override var mainUrl = "https://dismoiceline.uns.bio" }

class Doremifasol : VidStack() { override var mainUrl = "https://doremifasol.ezplayer.me" }
class MarcusP2P : VidStack() { override var mainUrl = "https://marcus.p2pstream.vip" }

class BllEmbedseek : VidStack() { override var mainUrl = "https://bll.embedseek.com" }

class NeocinEmbedseek : VidStack() { override var mainUrl = "https://neocine.embedseek.com" }

class Lukefirst : FilemoonV2() { override var mainUrl = "https://lukefirst.lol" }

class LuluVdo : LuluStream() { override var mainUrl = "https://luluvdo.com" }
class Bysebuho : FilemoonV2() { override var mainUrl = "https://bysebuho.com" }

class Videasy : ExtractorApi() {
    override var name = "Videasy"
    override var mainUrl = "https://player.videasy.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to "$mainUrl/",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            val resText = app.get(url, headers = headers, timeout = 5).text
            val m3u8 = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(resText)?.groupValues?.get(1)
            if (m3u8 != null) {
                callback.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                })
            }
        } catch (e: Exception) {
            Log.d("Movix", "Videasy error: ${e.message}")
        }
    }
}

class Vidlink : ExtractorApi() {
    override var name = "Vidlink"
    override var mainUrl = "https://vidlink.pro"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val reqHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to "$mainUrl/"
            )
            val resText = app.get(url, headers = reqHeaders, timeout = 5).text
            val m3u8 = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(resText)?.groupValues?.get(1)
            if (m3u8 != null) {
                callback.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                })
            }
        } catch (e: Exception) {
            Log.d("Movix", "Vidlink error: ${e.message}")
        }
    }
}


suspend fun parseM3u8MasterPlaylist(
    name: String,
    masterUrl: String,
    headers: Map<String, String>,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val uri = java.net.URI(masterUrl)
        val hostScheme = "${uri.scheme}://${uri.host}"
        val queryToken = if (masterUrl.contains("token=")) "token=" + masterUrl.substringAfter("token=") else ""

        val res = app.get(masterUrl, headers = headers, timeout = 10).text
        if (res.contains("#EXT-X-STREAM-INF")) {
            val lines = res.lines()
            var currentQuality = Qualities.Unknown.value

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val resolutionMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                    if (resolutionMatch != null) {
                        val height = resolutionMatch.groupValues[2].toIntOrNull()
                        if (height != null) {
                            currentQuality = when {
                                height >= 1080 -> Qualities.P1080.value
                                height >= 720  -> Qualities.P720.value
                                height >= 480  -> Qualities.P480.value
                                height >= 360  -> Qualities.P360.value
                                else           -> height
                            }
                        }
                    }
                } else if (line.isNotBlank() && !line.startsWith("#")) {
                    var subPath = line
                    if (subPath.contains("token=") && queryToken.isNotBlank()) {
                        subPath = subPath.substringBefore("token=") + queryToken
                    } else if (!subPath.contains("token=") && queryToken.isNotBlank()) {
                        subPath += if (subPath.contains("?")) "&$queryToken" else "?$queryToken"
                    }

                    val finalSubUrl = if (subPath.startsWith("http")) {
                        subPath
                    } else if (subPath.startsWith("/")) {
                        hostScheme + subPath
                    } else {
                        val basePath = masterUrl.substringBefore("?").substringBeforeLast("/") + "/"
                        basePath + subPath
                    }

                    val qualityName = when (currentQuality) {
                        Qualities.P1080.value -> "1080p"
                        Qualities.P720.value  -> "720p"
                        Qualities.P480.value  -> "480p"
                        Qualities.P360.value  -> "360p"
                        else                  -> "${currentQuality}p"
                    }

                    callback.invoke(newExtractorLink(name, "$name - $qualityName", finalSubUrl, ExtractorLinkType.M3U8) {
                        this.referer = ""
                        this.headers = headers
                        this.quality = currentQuality
                    })
                }
            }
        } else {
            callback.invoke(newExtractorLink(name, name, masterUrl, ExtractorLinkType.M3U8) {
                this.referer = ""
                this.headers = headers
                this.quality = Qualities.Unknown.value
            })
        }
    } catch (e: Exception) {
        Log.d("Movix", "parseM3u8MasterPlaylist error for $name: ${e.message}")
        callback.invoke(newExtractorLink(name, name, masterUrl, ExtractorLinkType.M3U8) {
            this.referer = ""
            this.headers = headers
            this.quality = Qualities.Unknown.value
        })
    }
}

suspend fun fetchVidsrcApi(
    name: String,
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val isMovie = url.contains("/movie/")
        val parts = url.split("/")
        val tmdbId = parts.getOrNull(parts.indexOf(if (isMovie) "movie" else "tv") + 1) ?: return
        val season = if (!isMovie) parts.getOrNull(parts.indexOf("tv") + 2) else null
        val episode = if (!isMovie) parts.getOrNull(parts.indexOf("tv") + 3) else null

        val apiUrl = if (isMovie) {
            "https://streamdata.vaplayer.ru/api.php?tmdb=$tmdbId&type=movie"
        } else {
            "https://streamdata.vaplayer.ru/api.php?tmdb=$tmdbId&type=tv&season=$season&episode=$episode"
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://nextgencloudfabric.com/"
        )

        val resText = app.get(apiUrl, headers = headers, timeout = 10).text
        val json = org.json.JSONObject(resText)
        val statusCode = json.optString("status_code")
        if (statusCode == "200" || statusCode == "200.0") {
            val dataObj = json.optJSONObject("data")
            val streamUrls = dataObj?.optJSONArray("stream_urls")
            if (streamUrls != null) {
                for (i in 0 until streamUrls.length()) {
                    val streamUrl = streamUrls.optString(i)
                    if (streamUrl.isNotBlank()) {
                        val serverPrefix = if (streamUrls.length() > 1) "$name (Server ${i + 1})" else name
                        parseM3u8MasterPlaylist(serverPrefix, streamUrl, headers, callback)
                    }
                }
            }

            val defaultSubs = json.optJSONArray("default_subs")
            if (defaultSubs != null) {
                for (i in 0 until defaultSubs.length()) {
                    val subObj = defaultSubs.optJSONObject(i) ?: continue
                    val subUrl = subObj.optString("url")
                    val subLang = subObj.optString("lang").ifBlank { "Unknown" }
                    if (subUrl.isNotBlank()) {
                        subtitleCallback.invoke(SubtitleFile(subLang, subUrl))
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.d("Movix", "$name Vidsrc error: ${e.message}")
    }
}

class VidsrcIo : ExtractorApi() {
    override var name = "Vidsrc.io"
    override var mainUrl = "https://vidsrc.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fetchVidsrcApi(name, url, subtitleCallback, callback)
    }
}

class VidsrcWtf : ExtractorApi() {
    override var name = "Vidsrc.wtf"
    override var mainUrl = "https://vidsrc.wtf"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fetchVidsrcApi(name, url, subtitleCallback, callback)
    }
}

class VidsrcCc : ExtractorApi() {
    override var name = "Vidsrc.cc"
    override var mainUrl = "https://vidsrc.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fetchVidsrcApi(name, url, subtitleCallback, callback)
    }
}

class VidsrcPm : ExtractorApi() {
    override var name = "Vidsrc.pm"
    override var mainUrl = "https://vidsrc.pm"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        fetchVidsrcApi(name, url, subtitleCallback, callback)
    }
}

private fun decryptPeachifyPayload(encryptedData: String): String? {
    try {
        val parts = encryptedData.split(".")
        if (parts.size != 3) return null
        fun decodeB64(s: String): ByteArray {
            val padLen = (4 - (s.length % 4)) % 4
            val padded = s + "=".repeat(padLen)
            return android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        }
        val iv = decodeB64(parts[0])
        val ciphertext = decodeB64(parts[1])
        val tag = decodeB64(parts[2])

        val cipherAndTag = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, cipherAndTag, 0, ciphertext.size)
        System.arraycopy(tag, 0, cipherAndTag, ciphertext.size, tag.size)

        val keyHex = "a8f2a1b5e9c470814f6b2c3a5d8e7f9c1a2b3c4d5e3f7a8b8cad1e2d0a4d5c5d"
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decryptedBytes = cipher.doFinal(cipherAndTag)
        return String(decryptedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        Log.d("Movix", "Peachify decrypt error: ${e.message}")
        return null
    }
}

class Peachify : ExtractorApi() {
    override var name = "Peachify"
    override var mainUrl = "https://peachify.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val isMovie = url.contains("/movie/")
            val parts = url.split("/")
            val tmdbId = parts.getOrNull(parts.indexOf(if (isMovie) "movie" else "tv") + 1) ?: return
            val season = if (!isMovie) parts.getOrNull(parts.indexOf("tv") + 2) else null
            val episode = if (!isMovie) parts.getOrNull(parts.indexOf("tv") + 3) else null

            val providers = listOf("holly", "air", "multi")
            val baseDomain = "https://usa.eat-peach.sbs"

            val reqHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Origin" to "https://peachify.top",
                "Referer" to "https://peachify.top/"
            )

            for (provider in providers) {
                val apiUrl = if (isMovie) {
                    "$baseDomain/$provider/movie/$tmdbId"
                } else {
                    "$baseDomain/$provider/tv/$tmdbId/$season/$episode"
                }

                try {
                    val resText = app.get(apiUrl, headers = reqHeaders, timeout = 8).text
                    val json = org.json.JSONObject(resText)
                    val encData = json.optString("data")
                    if (encData.isNotBlank()) {
                        val decryptedJsonStr = decryptPeachifyPayload(encData)
                        if (!decryptedJsonStr.isNullOrBlank()) {
                            val decObj = org.json.JSONObject(decryptedJsonStr)
                            val sources = decObj.optJSONArray("sources")
                            if (sources != null && sources.length() > 0) {
                                for (i in 0 until sources.length()) {
                                    val src = sources.optJSONObject(i) ?: continue
                                    val streamUrl = src.optString("url")
                                    val type = src.optString("type")
                                    val isHls = type == "hls" || streamUrl.contains(".m3u8")

                                    if (streamUrl.isNotBlank()) {
                                        var finalStreamUrl = streamUrl
                                        val finalHeaders = mutableMapOf<String, String>()

                                        if (streamUrl.contains("m3u8-proxy?url=")) {
                                            try {
                                                val innerEncoded = streamUrl.substringAfter("m3u8-proxy?url=").substringBefore("&")
                                                val innerDecoded = java.net.URLDecoder.decode(innerEncoded, "UTF-8")
                                                if (innerDecoded.isNotBlank()) {
                                                    finalStreamUrl = innerDecoded
                                                    finalHeaders["Referer"] = "https://goodstream.cc/"
                                                }
                                            } catch (e: Exception) {
                                                Log.d("Movix", "Unquote Peachify proxy error: ${e.message}")
                                            }
                                        }

                                        val linkType = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                        callback.invoke(newExtractorLink(name, "$name - $provider", finalStreamUrl, linkType) {
                                            this.referer = ""
                                            this.headers = finalHeaders
                                            this.quality = Qualities.Unknown.value
                                        })
                                    }
                                }

                                val subs = decObj.optJSONArray("subtitles")
                                if (subs != null) {
                                    for (s in 0 until subs.length()) {
                                        val subObj = subs.optJSONObject(s) ?: continue
                                        val subUrl = subObj.optString("url")
                                        val subLang = subObj.optString("lang").ifBlank { "Unknown" }
                                        if (subUrl.isNotBlank()) {
                                            subtitleCallback.invoke(SubtitleFile(subLang, subUrl))
                                        }
                                    }
                                }

                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("Movix", "Peachify provider $provider failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d("Movix", "Peachify error: ${e.message}")
        }
    }
}

private val mapper = jacksonObjectMapper()

open class MailRu : ExtractorApi() {
    override val name = "MailRu"
    override val mainUrl = "https://my.mail.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val initialreq = app.get(url)
        val responsetext = initialreq.text
        val videokey = initialreq.cookies["video_key"] ?: ""

        val metadataregex = """"metadataUrl"\s*:\s*"([^"]+)"""".toRegex()
        val metamatch = metadataregex.find(responsetext)
        val extractedmetapath = metamatch?.groupValues?.get(1) ?: return

        val fullmetaurl =
            if (extractedmetapath.startsWith("//")) "https:$extractedmetapath" else extractedmetapath

        val timestamp = System.currentTimeMillis()
        val finalajaxurl = if (fullmetaurl.contains("?")) {
            "$fullmetaurl&_=$timestamp&ajax_call=1&ext=1"
        } else {
            "$fullmetaurl?_=$timestamp&ajax_call=1&ext=1"
        }

        val outputrequest = app.get(
            finalajaxurl,
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Referer" to url,
                "X-Requested-With" to "XMLHttpRequest",
                "Sec-GPC" to "1",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            ),
            cookies = mapOf("video_key" to videokey)
        )

        val videodata = try {
            mapper.readValue<MailRuData>(outputrequest.text)
        } catch (e: Exception) {
            null
        }

        videodata?.videos?.forEach { video ->
            val videourl = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videourl,
                ) {
                    this.headers = mapOf("Cookie" to "video_key=$videokey")
                    this.quality = getQualityFromName(video.key)
                }
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MailRuData(
        @JsonProperty("videos") val videos: List<MailRuVideoData>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MailRuVideoData(
        @JsonProperty("url") val url: String = "",
        @JsonProperty("key") val key: String = ""
    )
}






