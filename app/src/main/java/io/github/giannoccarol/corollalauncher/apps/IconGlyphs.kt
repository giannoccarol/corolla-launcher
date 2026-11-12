package io.github.giannoccarol.corollalauncher.apps

import java.util.Locale

/**
 * Picks the bundled glyph for an app in the themed icon style: the brand's logo for well-known
 * apps, otherwise a symbol for what the app does. Head unit apps have vendor package names
 * (com.syu.ms, com.nwd.radio...) but plain labels ("Radio", "Musica"), so labels in the
 * supported languages are checked before package names. Pure logic, unit tested.
 */
object IconGlyphs {

    /** Exact packages of well-known apps. */
    private val PACKAGES = mapOf(
        "com.spotify.music" to "spotify",
        "com.spotify.lite" to "spotify",
        "com.google.android.apps.maps" to "googlemaps",
        "com.google.android.apps.mapslite" to "googlemaps",
        "com.waze" to "waze",
        "com.google.android.apps.youtube.music" to "youtubemusic",
        "com.google.android.youtube" to "youtube",
        "com.google.android.apps.youtube.kids" to "youtube",
        "com.google.android.projection.gearhead" to "androidauto",
        "com.whatsapp" to "whatsapp",
        "com.whatsapp.w4b" to "whatsapp",
        "org.telegram.messenger" to "telegram",
        "org.thunderdog.challegram" to "telegram",
        "deezer.android.app" to "deezer",
        "com.apple.android.music" to "applemusic",
        "com.soundcloud.android" to "soundcloud",
        "com.aspiro.tidal" to "tidal",
        "com.audible.application" to "audible",
        "com.netflix.mediaclient" to "netflix",
        "com.android.chrome" to "googlechrome",
        "com.google.android.gm" to "gmail",
        "com.android.vending" to "googleplay",
        "com.shazam.android" to "shazam",
        "au.com.shiftyjelly.pocketcasts" to "pocketcasts",
        "com.google.android.apps.googleassistant" to "googleassistant",
        "net.osmand" to "osmand",
        "net.osmand.plus" to "osmand",
        "com.here.app.maps" to "here",
        "app.organicmaps" to "organicmaps",
        "org.mozilla.firefox" to "firefox",
        "com.google.android.apps.photos" to "googlephotos",
        "com.google.android.apps.docs" to "googledrive",
        "com.plexapp.android" to "plex",
        "com.google.android.apps.messaging" to "googlemessages",
        "com.google.android.googlequicksearchbox" to "google",
        "com.google.android.calendar" to "googlecalendar",
        "org.thoughtcrime.securesms" to "signal",
        "fm.castbox.audiobook.radio.podcast" to "castbox",
        "com.bambuna.podcastaddict" to "podcastaddict",
        "com.apple.android.podcasts" to "applepodcasts",
    )

    /**
     * Brand names in labels, compared without spaces, for variants of the apps above (Lite,
     * Beta, a vendor's rebuild). Longer names first: "YouTube Music" is not "YouTube".
     */
    private val BRAND_LABELS = listOf(
        "youtubemusic" to "youtubemusic",
        "youtube" to "youtube",
        "googlemaps" to "googlemaps",
        "androidauto" to "androidauto",
        "spotify" to "spotify",
        "waze" to "waze",
        "whatsapp" to "whatsapp",
        "telegram" to "telegram",
        "deezer" to "deezer",
        "applemusic" to "applemusic",
        "soundcloud" to "soundcloud",
        "tidal" to "tidal",
        "audible" to "audible",
        "netflix" to "netflix",
        "chrome" to "googlechrome",
        "gmail" to "gmail",
        "playstore" to "googleplay",
        "shazam" to "shazam",
        "pocketcasts" to "pocketcasts",
        "osmand" to "osmand",
        "herewego" to "here",
        "organicmaps" to "organicmaps",
        "firefox" to "firefox",
        "castbox" to "castbox",
        "podcastaddict" to "podcastaddict",
    )

    /**
     * What an app does, from its label words or package name. Order matters: the first rule
     * that matches wins, so "Car settings" is a car and "Video player" is not music.
     *
     * Package hints of three letters or fewer must be a whole package segment ("com.syu.bt"),
     * longer ones may be part of one ("com.android.gallery3d").
     */
    private class Rule(val glyph: String, val words: List<String>, val packages: List<String>)

    private val RULES = listOf(
        Rule(
            "car",
            listOf("car", "auto", "vehicle", "veicolo", "fahrzeug", "vehicule", "véhicule", "vehiculo", "vehículo", "carinfo"),
            listOf("car", "vehicle", "carinfo", "canbus"),
        ),
        // Before "sound" (the equalizer): a sound recorder is a microphone.
        Rule(
            "assistant",
            listOf("recorder", "registratore", "rekorder", "grabadora", "enregistreur", "dictaphone"),
            listOf("recorder"),
        ),
        Rule(
            "maps",
            listOf(
                "maps", "map", "mappe", "mappa", "karten", "karte", "cartes", "mapas", "navi", "navigation",
                "navigazione", "navigatore", "navigator", "navegacion", "navegación", "gps",
            ),
            listOf("maps", "navi", "gps"),
        ),
        Rule("radio", listOf("radio", "fm", "am fm", "tuner", "rádio", "radiotuner"), listOf("radio", "fm", "tuner")),
        Rule(
            "video",
            listOf("video", "videos", "vidéo", "vídeo", "vidéos", "vídeos", "movies", "movie", "film", "films", "cinema"),
            listOf("video", "movie", "vplayer"),
        ),
        Rule(
            "music",
            listOf("music", "musica", "música", "musique", "musik", "musicale", "songs", "brani"),
            listOf("music", "musicplayer", "player", "mp3"),
        ),
        Rule("podcast", listOf("podcast", "podcasts"), listOf("podcast")),
        Rule(
            "gallery",
            listOf(
                "gallery", "galleria", "galerie", "galería", "galeria", "photos", "photo", "foto", "fotos",
                "bilder", "album", "images", "immagini", "imágenes", "imagenes",
            ),
            listOf("gallery", "photo", "album"),
        ),
        Rule(
            "camera",
            listOf(
                "camera", "fotocamera", "caméra", "kamera", "cámara", "camara", "dvr", "dashcam", "retrocamera",
                "rückfahrkamera",
            ),
            listOf("camera", "dvr", "dashcam", "cam"),
        ),
        Rule(
            "files",
            listOf(
                "files", "file", "fichiers", "dateien", "archivos", "archivio", "explorer", "documents",
                "documenti", "dokumente", "documentos",
            ),
            listOf("files", "filemanager", "fileexplorer", "explorer", "documentsui"),
        ),
        Rule("usb", listOf("usb"), listOf("usb")),
        Rule("aux", listOf("aux", "av in", "avin", "line in"), listOf("aux", "avin")),
        Rule("tv", listOf("tv", "dtv", "television", "televisione", "fernsehen", "télévision", "televisión"), listOf("tv", "dtv")),
        Rule("browser", listOf("browser", "internet", "web", "navigateur", "navegador"), listOf("browser")),
        Rule(
            "messages",
            listOf("messages", "messaggi", "sms", "mms", "nachrichten", "mensajes", "messagerie"),
            listOf("mms", "sms", "messaging"),
        ),
        Rule("contacts", listOf("contacts", "contatti", "rubrica", "kontakte", "contactos"), listOf("contacts")),
        Rule(
            "phone",
            listOf("phone", "telefono", "téléphone", "telephone", "telefon", "teléfono", "dialer", "calls", "chiamate", "anrufe", "llamadas"),
            listOf("dialer", "phone"),
        ),
        Rule("bluetooth", listOf("bluetooth", "bt"), listOf("bluetooth", "bt")),
        Rule(
            "equalizer",
            listOf(
                "equalizer", "equaliser", "eq", "equalizzatore", "égaliseur", "egaliseur", "ecualizador",
                "sound", "suono", "audio", "klang", "son", "sonido", "dsp",
            ),
            listOf("equalizer", "eq", "dsp", "audioeffect"),
        ),
        Rule(
            "settings",
            listOf(
                "settings", "setting", "impostazioni", "einstellungen", "paramètres", "parametres", "ajustes",
                "configuración", "configuracion", "setup",
            ),
            listOf("settings"),
        ),
        Rule("calendar", listOf("calendar", "calendario", "kalender", "calendrier"), listOf("calendar")),
        Rule("clock", listOf("clock", "orologio", "uhr", "horloge", "reloj", "alarm", "sveglia", "wecker"), listOf("clock", "alarm")),
        Rule(
            "calculator",
            listOf("calculator", "calcolatrice", "rechner", "calculatrice", "calculadora"),
            listOf("calculator", "calc"),
        ),
        Rule("weather", listOf("weather", "meteo", "météo", "wetter", "tiempo", "clima"), listOf("weather")),
        Rule("store", listOf("store", "shop", "market", "negozio", "tienda", "boutique"), listOf("store", "market")),
        Rule("mail", listOf("email", "e mail", "mail", "posta", "correo", "courriel"), listOf("email", "mail")),
        Rule(
            "assistant",
            listOf("assistant", "assistente", "assistent", "asistente", "voice", "voce", "sprache"),
            listOf("assistant", "voice"),
        ),
        Rule(
            "search",
            listOf("search", "cerca", "ricerca", "suche", "buscar", "búsqueda", "recherche", "rechercher"),
            listOf("search", "quicksearchbox"),
        ),
        Rule("downloads", listOf("downloads", "download", "scaricati", "descargas", "téléchargements"), listOf("download")),
    )

    /** Every glyph [match] can return: GlyphResources must have a drawable for each. */
    val ALL: Set<String> =
        (PACKAGES.values + BRAND_LABELS.map { it.second } + RULES.map { it.glyph }).toSet()

    /** The glyph for the app, or null when nothing fits (the caller draws one from the icon). */
    fun match(packageName: String, label: String): String? {
        PACKAGES[packageName]?.let { return it }
        val compact = label.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        BRAND_LABELS.firstOrNull { compact.contains(it.first) }?.let { return it.second }
        // Android Auto / CarPlay receivers (ZLink, Carlinkit...) get the car.
        if (ProjectionApps.find(listOf(ProjectionApps.Candidate(packageName, label))) != null) return "car"

        val words = " " + label.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") + " "
        RULES.firstOrNull { rule -> rule.words.any { words.contains(" $it ") } }?.let { return it.glyph }

        val segments = packageName.lowercase(Locale.ROOT).split('.', '_')
        return RULES.firstOrNull { rule ->
            rule.packages.any { hint ->
                if (hint.length <= 3) hint in segments else segments.any { it.contains(hint) }
            }
        }?.glyph
    }
}
