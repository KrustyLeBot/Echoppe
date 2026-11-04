package app.echoppe.filters

/** Resource types of filter options, as bits. */
object RequestType {
    const val SCRIPT = 1
    const val IMAGE = 1 shl 1
    const val STYLESHEET = 1 shl 2
    const val XHR = 1 shl 3
    const val SUBDOCUMENT = 1 shl 4
    const val MEDIA = 1 shl 5
    const val FONT = 1 shl 6
    const val OBJECT = 1 shl 7
    const val PING = 1 shl 8
    const val WEBSOCKET = 1 shl 9
    const val OTHER = 1 shl 10
    const val ALL = (1 shl 11) - 1

    /**
     * What a WebView request without a usable hint may be: fetches and scripts look alike. Not PING:
     * `*$ping,third-party` would then block every third-party fetch, payment SDKs included.
     */
    const val UNKNOWN = SCRIPT or XHR or OTHER

    val BY_NAME = mapOf(
        "script" to SCRIPT, "image" to IMAGE, "stylesheet" to STYLESHEET, "css" to STYLESHEET,
        "xmlhttprequest" to XHR, "xhr" to XHR, "subdocument" to SUBDOCUMENT, "frame" to SUBDOCUMENT,
        "media" to MEDIA, "font" to FONT, "object" to OBJECT, "object-subrequest" to OBJECT,
        "ping" to PING, "beacon" to PING, "websocket" to WEBSOCKET, "other" to OTHER,
    )

    private val EXTENSIONS = mapOf(
        "js" to SCRIPT, "mjs" to SCRIPT, "css" to STYLESHEET,
        "png" to IMAGE, "jpg" to IMAGE, "jpeg" to IMAGE, "gif" to IMAGE, "webp" to IMAGE, "avif" to IMAGE, "svg" to IMAGE, "ico" to IMAGE,
        "woff" to FONT, "woff2" to FONT, "ttf" to FONT, "otf" to FONT, "eot" to FONT,
        "mp4" to MEDIA, "webm" to MEDIA, "mp3" to MEDIA, "m3u8" to MEDIA, "ogg" to MEDIA,
    )

    /** Best guess from the URL path and the Accept header; [UNKNOWN] when neither says. */
    fun infer(url: String, accept: String?): Int {
        val a = accept?.lowercase().orEmpty()
        when {
            a.startsWith("text/css") -> return STYLESHEET
            a.startsWith("image/") -> return IMAGE
            a.startsWith("text/html") -> return SUBDOCUMENT
        }
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        if ('.' in last) EXTENSIONS[last.substringAfterLast('.').lowercase()]?.let { return it }
        return UNKNOWN
    }
}
