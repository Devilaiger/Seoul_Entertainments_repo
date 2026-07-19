package com.seoulentertainment.katmoviehd

import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.ExtractorLinkType
import com.seoulentertainment.app.utils.newExtractorLink
import com.seoulentertainment.app.app
import org.json.JSONObject
import android.util.Log

suspend fun newKatExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    initializer: suspend ExtractorLink.() -> Unit = { }
): ExtractorLink {
    var cleanUrl = url
    var refererUrl: String? = null
    if (cleanUrl.contains("pixeldrain")) {
        val host = Regex("https?://([^/]+)").find(cleanUrl)?.groupValues?.get(1) ?: "pixeldrain.com"
        val id = Regex("/(?:u|api/file)/([\\w-]+)").find(cleanUrl)?.groupValues?.get(1)
        if (!id.isNullOrEmpty()) {
            var fileName = "video.mkv"
            try {
                val infoJson = app.get("https://$host/api/file/$id/info").text
                val obj = JSONObject(infoJson)
                if (obj.optBoolean("success", false)) {
                    val parsedName = obj.optString("name")
                    if (!parsedName.isNullOrBlank()) {
                        fileName = parsedName
                    }
                }
            } catch (e: Exception) {
                Log.w("KatMovieHD", "Failed to fetch Pixeldrain info for $id: ${e.message}")
            }
            cleanUrl = "https://$host/api/file/$id"
            refererUrl = "https://$host/u/$id"
        }
    }
    return newExtractorLink(source, name, cleanUrl, type) {
        if (refererUrl != null) {
            this.referer = refererUrl
        }
        initializer()
    }
}

