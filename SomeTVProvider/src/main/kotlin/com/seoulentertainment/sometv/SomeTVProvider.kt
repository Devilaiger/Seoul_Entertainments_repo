package com.seoulentertainment.sometv

import com.seoulentertainment.app.*
import com.seoulentertainment.app.utils.*
import com.seoulentertainment.app.network.WebViewResolver
import org.jsoup.nodes.Element
import android.util.Log
import com.lagradost.nicehttp.NiceResponse


class SomeTVProvider : MainAPI() {
    companion object {
        private var resolvedUrl: String? = null
        private val domains = listOf(
            "https://sometv132.top",
            "https://sometv133.top",
            "https://sometv134.top",
            "https://sometv135.top",
            "https://sometv136.top"
        )

        private suspend fun resolveUrl(force: Boolean = false): String {
            if (!force) {
                resolvedUrl?.let { return it }
            }
            for (domain in domains) {
                try {
                    val response = app.get(domain, timeout = 5000L)
                    if (response.code == 200) {
                        resolvedUrl = domain
                        Log.i("SomeTV", "Successfully resolved working domain: $domain")
                        return domain
                    }
                } catch (e: Exception) {
                    Log.w("SomeTV", "Domain check failed for $domain: ${e.message}")
                }
            }
            val fallback = domains.first()
            resolvedUrl = fallback
            Log.w("SomeTV", "All domains failed or timed out. Falling back to default: $fallback")
            return fallback
        }

        private fun String.fixUrl(resolved: String): String {
            for (domain in domains) {
                if (this.startsWith(domain)) {
                    return this.replaceFirst(domain, resolved)
                }
            }
            return this
        }
    }

    private suspend fun safeGet(url: String): NiceResponse {
        var resolved = resolveUrl()
        var targetUrl = url.fixUrl(resolved)
        return try {
            val res = app.get(targetUrl)
            if (res.code == 200) {
                res
            } else {
                Log.w("SomeTV", "GET request to $targetUrl failed with code ${res.code}. Forcing domain re-resolution.")
                resolved = resolveUrl(force = true)
                app.get(url.fixUrl(resolved))
            }
        } catch (e: Exception) {
            Log.w("SomeTV", "GET request to $targetUrl failed with exception: ${e.message}. Forcing domain re-resolution.")
            resolved = resolveUrl(force = true)
            app.get(url.fixUrl(resolved))
        }
    }

    override var mainUrl: String
        get() = resolvedUrl ?: "https://sometv129.top"
        set(value) {
            resolvedUrl = value
        }

    override var name = "SomeTV"
    override var lang = "ko"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    override val mainPage: List<MainPageData>
        get() = mainPageOf(
            "$mainUrl/video?category1=2" to "Korean Drama",
            "$mainUrl/video?category1=1" to "Korean Movies",
            "$mainUrl/" to "Latest"
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) request.data
                  else "${request.data}&page=$page"
        val doc = safeGet(url).document
        val items = doc.select("div.video-card a[href*=\"/view/\"]")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = absUrl("href").ifEmpty { return null }
        val title = selectFirst("h4")?.text() ?: return null
        val poster = selectFirst("img")?.attr("src")
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=${query.replace(" ", "+")}"
        val doc = safeGet(url).document
        return doc.select("div.video-card a[href*=\"/view/\"]")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val baseUrl = url.split("?")[0]
        val doc = safeGet(baseUrl).document

        val title = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst("h2")?.text()
            ?: ""
        val poster = doc.selectFirst("img[src*=\"filesbest\"]")?.attr("src")
        val description = doc.selectFirst("div.줄거리 p")?.text()
            ?: doc.selectFirst("p.text-gray-300")?.text()
        val tags = doc.select("span.tag, a.tag, div[class*=\"tag\"] span")
            .map { it.text() }
            .filter { it.isNotBlank() }

        val episodeElements = doc.select("div.grid a[href*=\"source=\"]")
        val episodes = if (episodeElements.isEmpty()) {
            listOf(
                newEpisode(baseUrl) {
                    this.name = title
                    this.episode = 1
                }
            )
        } else {
            episodeElements.mapIndexed { index, el ->
                val epUrl = el.absUrl("href")
                val epLabel = el.selectFirst("h4")?.text() ?: "Episode ${index + 1}"
                val epPoster = el.selectFirst("img")?.attr("src")
                val epMatch = Regex("[Ee](\\d+)").find(epLabel)
                val epNum = epMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: (episodeElements.size - index)
                newEpisode(epUrl) {
                    this.name = epLabel
                    this.episode = epNum
                    this.posterUrl = epPoster
                }
            }.sortedBy { it.episode }
        }

        return newTvSeriesLoadResponse(title, baseUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.i("SomeTV", "loadLinks called with data: $data")
        val doc = safeGet(data).document
        val iframeSrc = doc.selectFirst("iframe")?.absUrl("src")
            ?: doc.selectFirst("div.video-player iframe")?.absUrl("src")
            ?: run {
                Log.i("SomeTV", "Failed to find iframe in $data")
                return false
            }
        Log.i("SomeTV", "Found iframeSrc: $iframeSrc")

        // Goodstream blocks all non-browser HTTP clients via TLS fingerprinting.
        // The ONLY working approach: play the iframe URL inside a WebView.
        // We pass iframeSrc as extractorData so SeoulEntertainmentIPlayer can detect
        // it and open a WebView overlay instead of handing the URL to ExoPlayer.
        //
        // We still need to fire the WebViewResolver to get a real m3u8 URL so that
        // the app knows this is an M3U8 link and routes to the player. The URL itself
        // is not used by ExoPlayer — it's intercepted in SeoulEntertainmentIPlayer.

        val resolver = WebViewResolver(
            interceptUrl = Regex(".*goodstream\\.one.*master\\.m3u8.*"),
            useOkhttp = false
        )
        val (m3u8Request, _) = resolver.resolveUsingWebView(url = iframeSrc)

        val m3u8Url = m3u8Request?.url?.toString() ?: run {
            // If WebView interception fails, use a dummy URL but still pass iframe to WebView player
            Log.w("SomeTV", "WebView interception failed — using iframe fallback URL")
            "https://goodstream.one/player-placeholder.m3u8"
        }
        Log.i("SomeTV", "m3u8Url (for routing): $m3u8Url")
        Log.i("SomeTV", "iframeSrc (for WebView player): $iframeSrc")

        // extractorData carries the iframe URL with an explicit marker so the app
        // does not confuse normal provider metadata with a WebView handoff.
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://goodstream.one/"
                this.quality = Qualities.Unknown.value
                this.extractorData = "goodstream-webview:$iframeSrc"
            }
        )
        return true
    }
}
