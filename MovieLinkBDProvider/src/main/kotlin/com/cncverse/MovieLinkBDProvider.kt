@file:Suppress("DEPRECATION")
package com.cncverse

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

class MovieLinkBDProvider : MainAPI() {
    companion object {
        var appContext: Context? = null
        // The site uses a rotating subdomain mirror; we store the resolved base
        // and fall back to movielinkbd.one if the mirror fails.
        private const val FALLBACK_URL = "https://movielinkbd.one"
        @Volatile private var lastBrowserOpenMs = 0L
        private const val BROWSER_DEBOUNCE_MS = 10_000L
    }

    override var mainUrl = "https://movielinkbd.one"
    override var name = "MovieLinkBD"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "/" to "Recently Updated",
        "/type/movies" to "All Movies",
        "/type/series" to "All Web Series",
        "/language/hindi" to "Hindi Movies",
        "/language/bangla" to "Bangla Movies",
        "/language/bangla-dubbed" to "Bangla Dubbed",
        "/language/dual-audio" to "Dual Audio",
        "/language/english" to "English",
        "/southIndian" to "South Indian",
        "/language/korean" to "Korean",
        "/anime" to "Anime Zone",
        "/drama" to "K/J/C Drama",
        "/ongoing" to "Ongoing Series",
        "/genre/action" to "Action",
        "/genre/thriller" to "Thriller",
        "/genre/horror" to "Horror",
        "/genre/romance" to "Romance",
        "/category/wwe" to "WWE"
    )

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.Anime,
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    // ── Resolve the live mirror URL ─────────────────────────────────────────
    // The canonical domain (movielinkbd.one) may redirect to a CDN mirror such as
    // https://sqghcr.movielinkbd.li/.  We follow the redirect once and cache it.
    @Volatile private var resolvedBase: String? = null

    private suspend fun getBase(): String {
        resolvedBase?.let { return it }

        // Fetch from cache first on startup if not initialized
        if (mainUrl == FALLBACK_URL) {
            try {
                val context = com.lagradost.cloudstream3.CloudStreamApp.context
                val prefs = context?.getSharedPreferences("com.lagradost.cloudstream3", Context.MODE_PRIVATE)
                prefs?.getString("movielinkbd_main_url", null)?.let { cachedUrl ->
                    mainUrl = cachedUrl
                }
            } catch (_: Exception) {}
        }

        var currentUrl = mainUrl
        var success = false
        var baseResult = FALLBACK_URL

        // Try the current mainUrl
        try {
            val html = httpGetText(currentUrl, headers)
            val doc = org.jsoup.Jsoup.parse(html, currentUrl)
            val newSiteAnchor = doc.select("a").firstOrNull { a ->
                val text = a.text().lowercase()
                text.contains("visit movielinkbd new site") || text.contains("new site")
            }
            val targetUrl = newSiteAnchor?.attr("abs:href")
                ?: doc.selectFirst("a[href*='movielinkbd']:not([href*='movielinkbd.one'])")?.attr("abs:href")
                ?: currentUrl

            val finalUrl = targetUrl.trimEnd('/')
            val uri = java.net.URI(finalUrl)
            baseResult = "${uri.scheme}://${uri.host}"
            resolvedBase = baseResult
            success = true

            // Save to cache if domain changed
            if (baseResult != mainUrl) {
                mainUrl = baseResult
                try {
                    val context = com.lagradost.cloudstream3.CloudStreamApp.context
                    val prefs = context?.getSharedPreferences("com.lagradost.cloudstream3", Context.MODE_PRIVATE)
                    prefs?.edit()?.putString("movielinkbd_main_url", baseResult)?.apply()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        if (!success) {
            resolvedBase = mainUrl
            baseResult = mainUrl
        }

        return baseResult
    }

        private fun openInExternalBrowser(url: String)   {
        // Disabled for safety/ad-free
    }

    // ── Homepage / category pages ───────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = getBase()
        val path = request.data
        val url = when {
            // Homepage: /, /page/2, /page/3 …
            path == "/" && page == 1 -> "$base/"
            path == "/" -> "$base/page/$page"
            // Category pages: /type/movies, /type/movies/page/2 …
            page == 1 -> "$base$path"
            else -> "$base$path/page/$page"
        }
        val doc = httpGetDoc(url, headers)
        val items = parseMovieCards(doc, base)
        return newHomePageResponse(HomePageList(request.name, items, isHorizontalImages = true), hasNext = items.isNotEmpty())
    }

    // ── Search ──────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        val base = getBase()
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val doc = httpGetDoc("$base/search?q=$encodedQuery", headers)
        return parseMovieCards(doc, base)
    }

    // ── Parse movie cards from listing pages ───────────────────────────────
    private fun parseMovieCards(doc: org.jsoup.nodes.Document, base: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()

        // The site renders movie cards as <a> tags wrapping poster images,
        // each followed by a title link.  Common wrapper selectors:
        val cards = doc.select("div.movie-item, div.item-box, div.film-item, div.post-item, .movie-card")

        if (cards.isNotEmpty()) {
            cards.forEach { card ->
                val aTag = card.selectFirst("a[href*='/movie/'], a[href*='/series/'], a[href*='/anime/'], a[href*='/download18plus/']")
                    ?: return@forEach
                val href = aTag.attr("abs:href").ifEmpty { base + aTag.attr("href") }
                val title = card.selectFirst(".title, .movie-title, h3, h2")?.text()?.trim()
                    ?: aTag.attr("title").trim()
                    ?: return@forEach
                val img = card.selectFirst("img")
                val poster = img?.attr("data-src")?.ifEmpty { img.attr("src") }
                    ?: img?.attr("src")

                val type = if (href.contains("/series/") || href.contains("/anime/"))
                    TvType.TvSeries else TvType.Movie

                results.add(newMovieSearchResponse(title, href, type) {
                    this.posterUrl = poster
                })
            }
            return results
        }

        // Fallback: collect all anchor links to /movie/, /series/, /anime/ paths
        // that contain a child <img> (these are the poster links)
        val movieLinkPattern = "a[href*='/movie/'], a[href*='/series/'], a[href*='/anime/'], a[href*='/download18plus/']"
        val seen = mutableSetOf<String>()
        doc.select(movieLinkPattern).forEach { a ->
            val href = a.attr("abs:href").ifEmpty { base + a.attr("href") }
            if (!seen.add(href)) return@forEach
            // Skip nav/footer links (they usually don't have images)
            val img = a.selectFirst("img") ?: return@forEach
            val poster = img.attr("data-src").ifEmpty { img.attr("src") }

            // Title: try sibling/parent text nodes, then strip the encoded ID from the href
            val titleEl = a.parent()?.selectFirst(".title, .movie-title, h3, h2, [class*='name']")
            val title = titleEl?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: a.attr("title").trim().takeIf { it.isNotEmpty() }
                ?: a.text().trim().takeIf { it.isNotEmpty() }
                ?: return@forEach

            val type = if (href.contains("/series/") || href.contains("/anime/"))
                TvType.TvSeries else TvType.Movie

            results.add(newMovieSearchResponse(title, href, type) {
                this.posterUrl = poster.takeIf { it.isNotEmpty() }
            })
        }

        // Another fallback: the read_url_content (markdown) showed that each
        // listing page simply has <a href="/movie/...">Title</a> text links.
        // If no images found at all, still return title-only cards.
        if (results.isEmpty()) {
            doc.select(movieLinkPattern).forEach { a ->
                val href = a.attr("abs:href").ifEmpty { base + a.attr("href") }
                if (!seen.add(href)) return@forEach
                val title = a.text().trim().takeIf { it.isNotEmpty() } ?: return@forEach
                // Skip nav items (short single words like "HOME", "HINDI", etc.)
                if (title.length < 4 || title.all { it.isUpperCase() || it == ' ' }) return@forEach
                val type = if (href.contains("/series/") || href.contains("/anime/"))
                    TvType.TvSeries else TvType.Movie
                results.add(newMovieSearchResponse(title, href, type))
            }
        }

        return results
    }


    // ── Detail page ─────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        val base = getBase()
        val doc = httpGetDoc(url, headers)

        // Title
         val rawTitle = doc.selectFirst(".movie-info-view h2, h1, .movie-title, .film-title")?.text()?.trim()
            ?: doc.title().substringBefore("•").trim()

        // Year
        val year = Regex("\\((\\d{4})\\)").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        // Poster
        val poster = doc.selectFirst("img.poster, img[class*='poster'], .poster img, .thumb img, img[src*='poster'], img[src*='uploads']")
            ?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            ?.takeIf { it.isNotEmpty() }

        // Meta info block — the site renders label: value pairs
        fun metaVal(label: String): String? {
            return doc.select("li, p, span, div").firstOrNull { el ->
                el.text().contains(label, ignoreCase = true)
            }?.text()?.substringAfter(":")?.trim()
        }

        // Storyline / plot
        val plot = doc.selectFirst(".storyline p, .storyline, [class*='story'] p, [class*='plot']")
            ?.text()?.trim()
            ?: metaVal("Storyline")

        // Genre, cast, language
        val genre = metaVal("Genre")
        val cast = metaVal("Cast")
        val language = metaVal("Language")
        val rating = doc.selectFirst("[class*='imdb'], [class*='rating']")?.text()
            ?.let { Regex("[0-9.]+").find(it)?.value?.toFloatOrNull() }

        val fullPlot = buildString {
            language?.let { append("Language: $it\n") }
            genre?.let { append("Genre: $it\n") }
            cast?.let { append("Cast: $it\n") }
            plot?.let { append("\n$it") }
        }.trim()

        // Determine if this is a series or movie by the URL path
        val isSeries = url.contains("/series/") || url.contains("/anime/")

        // ── Download links ──────────────────────────────────────────────────
        // Each quality button is an <a href="/getLink/..."> on the detail page.
        // We collect them grouped by episode sections (for series) or flat (for movies).

        // All getLink anchors
        val linkAnchors = doc.select("a[href*='/getLink/']")
        // Watch online anchors
        val watchAnchors = doc.select("a[href*='/getWatch/']")

        if (!isSeries) {
            // Movie: collect all download links as quality|getLink pairs
            val linksData = (linkAnchors + watchAnchors).mapNotNull { a ->
                val href = a.attr("abs:href").ifEmpty {
                    val h = a.attr("href")
                    if (h.startsWith("http")) h else "$base$h"
                }
                val fixedHref = fixUrlDomain(href, base)
                val text = a.text().trim()
                val quality = extractQualityLabel(text)
                "$quality|$fixedHref|$url"
            }.joinToString(" ; ")

            return newMovieLoadResponse(rawTitle, url, TvType.Movie, linksData) {
                this.posterUrl = poster
                this.year = year
                this.plot = fullPlot.takeIf { it.isNotEmpty() }
                this.score = rating?.let { Score.from10(it) }
            }
        }

        // Series: group link anchors by episode sections
        val episodesData = mutableListOf<Episode>()

        // Try ep-card components first
        val epCards = doc.select("div.ep-card, [data-ep]")
        if (epCards.isNotEmpty()) {
            epCards.forEach { card ->
                val epText = card.attr("data-ep").ifEmpty {
                    card.selectFirst("h1, h2, h3, h4, h5, h6")?.text() ?: ""
                }
                val epNum = Regex("\\d+").find(epText)?.value?.toIntOrNull() ?: 1
                val cardLinks = card.select("a[href*='/getLink/'], a[href*='/getWatch/']")
                if (cardLinks.isNotEmpty()) {
                    val epUrl = cardLinks.mapNotNull { a ->
                        val href = a.attr("abs:href").ifEmpty {
                            val h = a.attr("href")
                            if (h.startsWith("http")) h else "$base$h"
                        }
                        val fixedHref = fixUrlDomain(href, base)
                        val quality = extractQualityLabel(a.text())
                        "$quality|$fixedHref|$url"
                    }.joinToString(" ; ")
                    
                    val existingEp = episodesData.find { it.episode == epNum && it.season == 1 }
                    if (existingEp != null) {
                        if (!existingEp.data.isNullOrEmpty()) {
                            existingEp.data = existingEp.data + " ; " + epUrl
                        } else {
                            existingEp.data = epUrl
                        }
                    } else {
                        episodesData.add(newEpisode(epUrl) {
                            this.name = "Episode $epNum"
                            this.season = 1
                            this.episode = epNum
                        })
                    }
                }
            }
        }

        // If no ep-cards found, fall back to legacy structured episode sections
        if (episodesData.isEmpty()) {
            val episodeSections = doc.select(
                "div.episode-section, div.season-section, h3:contains(Episode), h4:contains(Episode), h5:contains(Episode), " +
                "div[class*='episode'], div[class*='season'], strong:contains(Ep), b:contains(Ep)"
            )

            if (episodeSections.isNotEmpty()) {
                episodeSections.forEach { section ->
                    val sectionText = section.text()
                    val epRange = Regex("(?:Ep|Episode)[^\\d]*(\\d+)(?:[^\\d]+(\\d+))?", RegexOption.IGNORE_CASE)
                        .find(sectionText)
                    val start = epRange?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val end = epRange?.groupValues?.get(2)?.toIntOrNull() ?: start

                    // Collect links from siblings after this section heading
                    val sectionLinks = mutableListOf<String>()
                    var sib = section.nextElementSibling()
                    while (sib != null && !sib.tagName().matches(Regex("h[1-6]"))) {
                        val anchors = mutableListOf<org.jsoup.nodes.Element>()
                        if (sib.tagName() == "a" && (sib.attr("href").contains("/getLink/") || sib.attr("href").contains("/getWatch/"))) {
                            anchors.add(sib)
                        }
                        anchors.addAll(sib.select("a[href*='/getLink/'], a[href*='/getWatch/']"))

                        anchors.forEach { a ->
                            val href = a.attr("abs:href").ifEmpty {
                                val h = a.attr("href")
                                if (h.startsWith("http")) h else "$base$h"
                            }
                            val fixedHref = fixUrlDomain(href, base)
                            val quality = extractQualityLabel(a.text())
                            sectionLinks.add("$quality|$fixedHref|$url")
                        }
                        sib = sib.nextElementSibling()
                    }

                    if (sectionLinks.isNotEmpty()) {
                        val epUrl = sectionLinks.joinToString(" ; ")
                        for (epNum in start..end) {
                            val existingEp = episodesData.find { it.episode == epNum && it.season == 1 }
                            if (existingEp != null) {
                                if (!existingEp.data.isNullOrEmpty()) {
                                    existingEp.data = existingEp.data + " ; " + epUrl
                                } else {
                                    existingEp.data = epUrl
                                }
                            } else {
                                episodesData.add(newEpisode(epUrl) {
                                    this.name = "Episode $epNum"
                                    this.season = 1
                                    this.episode = epNum
                                })
                            }
                        }
                    }
                }
            }
        }

        // Fallback: if no structured sections found, treat all links as a single batch
        if (episodesData.isEmpty() && linkAnchors.isNotEmpty()) {
            val allLinks = (linkAnchors + watchAnchors).mapNotNull { a ->
                val href = a.attr("abs:href").ifEmpty {
                    val h = a.attr("href")
                    if (h.startsWith("http")) h else "$base$h"
                }
                val fixedHref = fixUrlDomain(href, base)
                val quality = extractQualityLabel(a.text())
                "$quality|$fixedHref|$url"
            }.joinToString(" ; ")

            episodesData.add(newEpisode(allLinks) {
                this.name = "Full Season"
                this.season = 1
                this.episode = 1
            })
        }

        // Sort episodes ascending so they are displayed logically in order
        episodesData.sortBy { it.episode }

        return newTvSeriesLoadResponse(rawTitle, url, TvType.TvSeries, episodesData) {
            this.posterUrl = poster
            this.year = year
            this.plot = fullPlot.takeIf { it.isNotEmpty() }
            this.score = rating?.let { Score.from10(it) }
        }
    }

    // ── Load links (resolve getLink → direct file URL) ──────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val base = getBase()
        val items = data.split(" ; ")
        val deferreds = items.map { item ->
            async {
                try {
                    val parts = item.split("|")
                    val qualityLabel = parts.getOrNull(0)?.trim() ?: ""
                    var linkUrl = parts.getOrNull(1)?.trim() ?: item.trim()
                    if (linkUrl.isNotEmpty()) {
                        // Extract refererUrl if present, else fallback to base URL
                        val refererUrl = parts.getOrNull(2)?.trim()?.let { fixUrlDomain(it, base) } ?: base

                        // Fix the domain of linkUrl dynamically
                        linkUrl = fixUrlDomain(linkUrl, base)

                        when {
                            // getLink page → resolve to direct URL
                            linkUrl.contains("/getLink/") -> {
                                resolveGetLink(linkUrl, qualityLabel, refererUrl, callback)
                            }
                            // getWatch page → resolve to stream URL
                            linkUrl.contains("/getWatch/") -> {
                                resolveGetWatch(linkUrl, qualityLabel, refererUrl, callback)
                            }
                            // Direct file link
                            linkUrl.contains("/file/") -> {
                                resolveDirectFile(linkUrl, qualityLabel, refererUrl, callback)
                            }
                            else -> {
                                // Try loading as generic extractor link
                                com.lagradost.cloudstream3.utils.loadExtractor(linkUrl, refererUrl, subtitleCallback, callback)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        deferreds.awaitAll()
        true
    }

    // ── Resolve /getLink/ to direct download URL ─────────────────────────────
    private suspend fun resolveGetLink(
        getLinkUrl: String,
        qualityLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val base = getBase()
            val reqHeaders = this@MovieLinkBDProvider.headers + mapOf("Referer" to refererUrl)
            val doc = httpGetDoc(getLinkUrl, reqHeaders)

            // 1. Check for iframe or video source embedded directly in the page (in case it acts as Watch Online)
            val videoSrc = doc.selectFirst("video source, video[src]")?.attr("src")
                ?: doc.selectFirst("iframe[src]")?.attr("src")
            if (!videoSrc.isNullOrEmpty()) {
                val streamUrl = if (videoSrc.startsWith("http")) videoSrc else "$base$videoSrc"
                val fixedStreamUrl = fixUrlDomain(streamUrl, base)
                if (fixedStreamUrl.contains("xcloud") || fixedStreamUrl.contains("mcloud")) {
                    resolveXCloud(fixedStreamUrl, qualityLabel, callback)
                    return
                } else if (fixedStreamUrl.startsWith("http") && !fixedStreamUrl.contains("movielinkbd") && !fixedStreamUrl.contains("telegram")) {
                    com.lagradost.cloudstream3.utils.loadExtractor(
                        fixedStreamUrl, getLinkUrl,
                        subtitleCallback = {},
                        callback = callback
                    )
                    return
                } else if (!fixedStreamUrl.contains("movielinkbd") && !fixedStreamUrl.contains("telegram")) {
                    val quality = labelToQuality(qualityLabel)
                    val type = if (fixedStreamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name [Stream]",
                            url = fixedStreamUrl,
                            referer = getLinkUrl,
                            quality = quality,
                            type = type,
                            headers = mapOf(
                                "User-Agent" to this@MovieLinkBDProvider.headers["User-Agent"]!!,
                                "Referer" to getLinkUrl
                            )
                        )
                    )
                    return
                }
            }

            // 2. ONE CLICK DOWNLOAD → /file/... direct link
            val fileAnchor = doc.selectFirst("a[href*='/file/']")
            if (fileAnchor != null) {
                val href = fileAnchor.attr("href")
                val fileUrl = if (href.startsWith("http")) href else "$base$href"
                val fixedFileUrl = fixUrlDomain(fileUrl, base)
                resolveDirectFile(fixedFileUrl, qualityLabel, getLinkUrl, callback)
            }

            // 3. Check for external anchor links (e.g. mCloud)
            // NOTE: Use for-loop (not forEach) so suspend calls inside work correctly.
            for (a in doc.select("a[href]")) {
                val href = a.attr("href").trim()
                if (href.isEmpty() || href.contains("/file/")) continue
                if (href.startsWith("http") && !href.contains("movielinkbd") &&
                    !href.contains("telegram") && !href.contains("google.com/store")) {
                    if (href.contains("xcloud") || href.contains("mcloud")) {
                        resolveXCloud(href, qualityLabel, callback)
                    } else {
                        // Wrap in try/catch — loadExtractor throws for unknown extractors,
                        // and we must NOT let that abort processing of the xcloud anchor.
                        try {
                            com.lagradost.cloudstream3.utils.loadExtractor(
                                href, getLinkUrl,
                                subtitleCallback = {},
                                callback = callback
                            )
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    // ── Resolve /getWatch/ to stream URL ────────────────────────────────────
    private suspend fun resolveGetWatch(
        getWatchUrl: String,
        qualityLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val base = getBase()
            val requestHeaders = this@MovieLinkBDProvider.headers + mapOf("Referer" to refererUrl)
            val html = httpGetText(getWatchUrl, requestHeaders)
            val doc = org.jsoup.Jsoup.parse(html, getWatchUrl)

            // 1. Check for /watch/ anchor (leads to actual player page)
            val watchAnchor = doc.selectFirst("a[href*='/watch/']")
            if (watchAnchor != null) {
                val href = watchAnchor.attr("href")
                val watchUrl = if (href.startsWith("http")) href else "$base$href"
                val fixedWatchUrl = fixUrlDomain(watchUrl, base)
                
                // Fetch the watch player page
                val watchHeaders = this@MovieLinkBDProvider.headers + mapOf("Referer" to getWatchUrl)
                val watchHtml = httpGetText(fixedWatchUrl, watchHeaders)
                val unescapedWatchHtml = watchHtml.replace("\\/", "/")

                val srcRegex = Regex("""const\s+SRC\s*=\s*["'](https?://[^"']+)["']""")
                val watchRegex = Regex("""(https?://[^\s'"]+/watch/[^\s'"]*)""")
                val m3u8Regex = Regex("""(https?://[^\s'"]+\.m3u8[^\s'"]*)""")
                val mp4Regex = Regex("""(https?://[^\s'"]+\.(?:mp4|mkv)[^\s'"]*)""")

                val streamUrl = srcRegex.find(unescapedWatchHtml)?.groupValues?.get(1)
                    ?: watchRegex.find(unescapedWatchHtml)?.value
                    ?: m3u8Regex.find(unescapedWatchHtml)?.value 
                    ?: mp4Regex.find(unescapedWatchHtml)?.value

                if (!streamUrl.isNullOrEmpty()) {
                    val resolvedUrl = if (streamUrl.startsWith("http")) streamUrl else "$base$streamUrl"
                    val fixedStreamUrl = fixUrlDomain(resolvedUrl, base)
                    if (fixedStreamUrl.contains("xcloud") || fixedStreamUrl.contains("mcloud")) {
                        resolveXCloud(fixedStreamUrl, qualityLabel, callback)
                        return
                    }
                    val quality = labelToQuality(qualityLabel)
                    val type = if (fixedStreamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name Watch Online [$qualityLabel]",
                            url = fixedStreamUrl,
                            referer = fixedWatchUrl,
                            quality = quality,
                            type = type,
                            headers = mapOf(
                                "User-Agent" to this@MovieLinkBDProvider.headers["User-Agent"]!!,
                                "Referer" to fixedWatchUrl
                            )
                        )
                    )
                    return
                }
            }

            // 2. Check for /file/ anchor as fallback
            val fileAnchor = doc.selectFirst("a[href*='/file/']")
            if (fileAnchor != null) {
                val href = fileAnchor.attr("href")
                val fileUrl = if (href.startsWith("http")) href else "$base$href"
                val fixedFileUrl = fixUrlDomain(fileUrl, base)
                resolveDirectFile(fixedFileUrl, qualityLabel, getWatchUrl, callback)
                return
            }

            // 3. Fallback: search for inline video/iframe tags
            val videoSrc = doc.selectFirst("video source, video[src]")?.attr("src")
                ?: doc.selectFirst("iframe[src]")?.attr("src")
            if (!videoSrc.isNullOrEmpty()) {
                val resolvedUrl = if (videoSrc.startsWith("http")) videoSrc else "$base$videoSrc"
                val fixedResolvedUrl = fixUrlDomain(resolvedUrl, base)
                if (fixedResolvedUrl.contains("xcloud") || fixedResolvedUrl.contains("mcloud")) {
                    resolveXCloud(fixedResolvedUrl, qualityLabel, callback)
                } else if (fixedResolvedUrl.startsWith("http") && !fixedResolvedUrl.contains("movielinkbd")) {
                    com.lagradost.cloudstream3.utils.loadExtractor(
                        fixedResolvedUrl, getWatchUrl,
                        subtitleCallback = {},
                        callback = callback
                    )
                } else {
                    val quality = labelToQuality(qualityLabel)
                    val type = if (fixedResolvedUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name [Stream]",
                            url = fixedResolvedUrl,
                            referer = getWatchUrl,
                            quality = quality,
                            type = type,
                            headers = headers
                        )
                    )
                }
            }
        } catch (_: Exception) { }
    }

    // ── Custom XCloud / mCloud Resolver ──────────────────────────────────────
    // Strategy: try a fast regular HTTP request first (no WebView/CF bypass).
    // Many CF-protected embed pages work fine with proper headers.  Only if the
    // simple request returns no usable stream URL do we fall back to cfClient
    // (which opens a WebView to solve the interactive CF challenge).
    // All qualities run in PARALLEL — no mutex — so we never hit serial timeout.
    private suspend fun resolveXCloud(
        xcloudUrl: String,
        qualityLabel: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "XCloudResolver"
        android.util.Log.d(TAG, "[$qualityLabel] resolveXCloud called: $xcloudUrl")

        val quality = labelToQuality(qualityLabel)
        val userAgent = this@MovieLinkBDProvider.headers["User-Agent"] ?: ""

        // Build the /stream_TOKEN URL (xcloud embed player format)
        val streamPlayerUrl = if (xcloudUrl.contains("/stream_")) {
            xcloudUrl
        } else {
            xcloudUrl.replace(Regex("""/([a-zA-Z0-9_=+/-]+)$"""), "/stream_$1")
        }
        android.util.Log.d(TAG, "[$qualityLabel] streamPlayerUrl: $streamPlayerUrl")

        // Helper: extract a playable media URL from raw HTML
        fun extractStreamUrl(html: String, source: String): String? {
            if (html.length < 100) return null  // too short → probably an error page
            val unescaped = html.replace("\\/", "/")
            android.util.Log.d(TAG, "[$qualityLabel][$source] len=${html.length} hasSRC=${html.contains("SRC")} hasM3u8=${html.contains("m3u8")} hasMp4=${html.contains(".mp4")}")

            val srcRegex     = Regex("""const\s+SRC\s*=\s*["'](https?://[^"']+)["']""")
            val fileRegex    = Regex("""(?:file|src)\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4|mkv)[^"']*)""")
            val m3u8Regex    = Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]*)""")
            val mp4Regex     = Regex("""(https?://[^\s'"<>]+\.(?:mp4|mkv)[^\s'"<>]*)""")
            val redirectRegex = Regex("""file\s*:\s*["'](/apis/redirect/[^"']+)""")

            val found = srcRegex.find(unescaped)?.groupValues?.get(1)
                ?: fileRegex.find(unescaped)?.groupValues?.get(1)
                ?: m3u8Regex.find(unescaped)?.value
                ?: mp4Regex.find(unescaped)?.value

            if (!found.isNullOrEmpty()) {
                android.util.Log.d(TAG, "[$qualityLabel][$source] found: $found")
                return found
            }
            val redirectPath = redirectRegex.find(unescaped)?.groupValues?.get(1)
            if (!redirectPath.isNullOrEmpty()) {
                val uri = runCatching { java.net.URI(streamPlayerUrl) }.getOrNull()
                val full = uri?.let { "${it.scheme}://${it.host}$redirectPath" }
                if (!full.isNullOrEmpty()) {
                    android.util.Log.d(TAG, "[$qualityLabel][$source] redirect: $full")
                    return full
                }
            }
            android.util.Log.d(TAG, "[$qualityLabel][$source] no stream found. snippet: ${html.take(300)}")
            return null
        }

        var streamUrl: String? = null

        // ── Pass 1: simple app.get() — fast, no WebView ──────────────────────
        val simpleHeaders = mapOf(
            "User-Agent" to userAgent,
            "Referer" to xcloudUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5"
        )
        for (tryUrl in listOf(streamPlayerUrl, xcloudUrl)) {
            if (!streamUrl.isNullOrEmpty()) break
            try {
                val html = app.get(tryUrl, headers = simpleHeaders).text
                streamUrl = extractStreamUrl(html, "simple")
            } catch (e: Exception) {
                android.util.Log.d(TAG, "[$qualityLabel] simple GET failed for $tryUrl: ${e.message}")
            }
        }

        // ── Pass 2: cfClient (WebView CF bypass) if simple failed ─────────────
        if (streamUrl.isNullOrEmpty()) {
            android.util.Log.d(TAG, "[$qualityLabel] simple GET got no stream — trying cfClient (WebView)")
            for (tryUrl in listOf(streamPlayerUrl, xcloudUrl)) {
                if (!streamUrl.isNullOrEmpty()) break
                try {
                    val html = httpGetText(tryUrl, simpleHeaders)
                    streamUrl = extractStreamUrl(html, "cfClient")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "[$qualityLabel] cfClient failed for $tryUrl: ${e.message}")
                }
            }
        }

        android.util.Log.d(TAG, "[$qualityLabel] final streamUrl: $streamUrl")

        // Only emit if we resolved a real media URL
        if (!streamUrl.isNullOrEmpty()) {
            val type = if (streamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            callback(
                ExtractorLink(
                    source = "XCloud",
                    name = "XCloud [$qualityLabel]",
                    url = streamUrl,
                    referer = streamPlayerUrl,
                    quality = quality,
                    type = type,
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to streamPlayerUrl
                    )
                )
            )
        } else {
            android.util.Log.w(TAG, "[$qualityLabel] no XCloud link emitted — stream URL could not be resolved")
        }
    }

    // ── Custom Direct File Resolver ──────────────────────────────────────────
    private suspend fun resolveDirectFile(
        fileUrl: String,
        qualityLabel: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val base = getBase()
            val requestHeaders = this@MovieLinkBDProvider.headers + mapOf("Referer" to refererUrl)
            val html = httpGetText(fileUrl, requestHeaders)
            val unescapedHtml = html.replace("\\/", "/")

            val srcRegex = Regex("""const\s+SRC\s*=\s*["'](https?://[^"']+)["']""")
            val watchRegex = Regex("""(https?://[^\s'"]+/watch/[^\s'"]*)""")
            val m3u8Regex = Regex("""(https?://[^\s'"]+\.m3u8[^\s'"]*)""")
            val mp4Regex = Regex("""(https?://[^\s'"]+\.(?:mp4|mkv)[^\s'"]*)""")

            val streamUrl = srcRegex.find(unescapedHtml)?.groupValues?.get(1)
                ?: watchRegex.find(unescapedHtml)?.value
                ?: m3u8Regex.find(unescapedHtml)?.value 
                ?: mp4Regex.find(unescapedHtml)?.value

            if (!streamUrl.isNullOrEmpty()) {
                val resolvedUrl = if (streamUrl.startsWith("http")) streamUrl else "$base$streamUrl"
                val fixedStreamUrl = fixUrlDomain(resolvedUrl, base)
                val quality = labelToQuality(qualityLabel)
                val type = if (fixedStreamUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name Direct [$qualityLabel]",
                        url = fixedStreamUrl,
                        referer = fileUrl,
                        quality = quality,
                        type = type,
                        headers = mapOf(
                            "User-Agent" to this@MovieLinkBDProvider.headers["User-Agent"]!!,
                            "Referer" to fileUrl
                        )
                    )
                )
            }
        } catch (_: Exception) { }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun fixUrlDomain(url: String, base: String): String {
        if (url.isEmpty()) return url
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return url
        }
        if (uri.host == null || !uri.host.contains("movielinkbd") || uri.host.contains("play")) return url
        val path = uri.rawPath ?: ""
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        return "${base.trimEnd('/')}/${path.trimStart('/')}$query$fragment"
    }

    private fun extractQualityLabel(text: String): String {
        return when {
            text.contains("4K", ignoreCase = true) || text.contains("2160", ignoreCase = true) -> "4K"
            text.contains("1080", ignoreCase = true) -> "1080p"
            text.contains("720p HEVC", ignoreCase = true) || text.contains("720 HEVC", ignoreCase = true) -> "720p HEVC"
            text.contains("720", ignoreCase = true) -> "720p"
            text.contains("480", ignoreCase = true) -> "480p"
            text.contains("360", ignoreCase = true) -> "360p"
            text.contains("Watch Online", ignoreCase = true) -> "Stream"
            text.contains("Download", ignoreCase = true) -> "Download"
            else -> text.take(30).trim().ifEmpty { "Unknown" }
        }
    }

    private fun labelToQuality(label: String): Int {
        return when {
            label.contains("4K", ignoreCase = true) || label.contains("2160", ignoreCase = true) -> Qualities.P2160.value
            label.contains("1080", ignoreCase = true) -> Qualities.P1080.value
            label.contains("720", ignoreCase = true) -> Qualities.P720.value
            label.contains("480", ignoreCase = true) -> Qualities.P480.value
            label.contains("360", ignoreCase = true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private val cfClient by lazy {
        val killer = com.lagradost.cloudstream3.network.CloudflareKiller()
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .addInterceptor(killer)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private suspend fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            val reqHeaders = okhttp3.Headers.Builder()
            headers.forEach { (k, v) -> reqHeaders.add(k, v) }
            val request = okhttp3.Request.Builder()
                .url(url)
                .headers(reqHeaders.build())
                .get()
                .build()
            val response = cfClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            body
        }
    }

    private suspend fun httpGetDoc(url: String, headers: Map<String, String> = emptyMap()): org.jsoup.nodes.Document {
        val html = httpGetText(url, headers)
        return org.jsoup.Jsoup.parse(html, url)
    }
}
