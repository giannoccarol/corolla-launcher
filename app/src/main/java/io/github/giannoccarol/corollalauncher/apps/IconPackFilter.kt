package io.github.giannoccarol.corollalauncher.apps

/**
 * The content of an icon pack's appfilter.xml, the format shared by Nova, ADW, Lawnchair and
 * most launchers: which drawable replaces which activity, plus the optional background, mask,
 * overlay and scale used to fit in apps the pack does not cover. Pure logic, unit tested; the
 * XML itself is read by [IconPack].
 */
class IconPackFilter private constructor(
    private val components: Map<String, String>,
    private val packages: Map<String, String>,
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float,
) {

    /** True when the pack can restyle apps it has no drawable for. */
    val canFitUncovered: Boolean get() = backs.isNotEmpty() || mask != null || upon != null

    /**
     * Drawable name for the activity, or for another activity of the same app when the pack
     * only knows an older activity name.
     */
    fun drawableFor(packageName: String, className: String): String? =
        components[key(packageName, className)] ?: packages[packageName]

    /** Background for an uncovered app; the same app always gets the same one. */
    fun backFor(packageName: String): String? =
        if (backs.isEmpty()) null else backs[Math.floorMod(packageName.hashCode(), backs.size)]

    /** Feed it the start tags of appfilter.xml in order. */
    class Builder {
        private val components = LinkedHashMap<String, String>()
        private val packages = LinkedHashMap<String, String>()
        private val backs = ArrayList<String>()
        private var mask: String? = null
        private var upon: String? = null
        private var scale = 1f

        fun tag(name: String, attribute: (String) -> String?) {
            when (name) {
                "item" -> {
                    val drawable = attribute("drawable")?.takeIf { it.isNotBlank() } ?: return
                    val (pkg, cls) = parseComponent(attribute("component") ?: return) ?: return
                    components.putIfAbsent(key(pkg, cls), drawable)
                    packages.putIfAbsent(pkg, drawable)
                }
                "iconback" -> backs += images(attribute)
                "iconmask" -> mask = images(attribute).firstOrNull() ?: mask
                "iconupon" -> upon = images(attribute).firstOrNull() ?: upon
                "scale" -> scale = attribute("factor")?.toFloatOrNull()?.takeIf { it in 0.1f..2f } ?: scale
            }
        }

        fun build() = IconPackFilter(components, packages, backs.toList(), mask, upon, scale)

        /** Image attributes are img, img1, img2...; some packs skip numbers. */
        private fun images(attribute: (String) -> String?): List<String> =
            (listOf("img") + (1..MAX_IMAGES).map { "img$it" })
                .mapNotNull { attribute(it)?.takeIf(String::isNotBlank) }
    }

    companion object {
        private const val MAX_IMAGES = 9

        /**
         * "ComponentInfo{com.app/com.app.Main}" to the package and the full class name. Short
         * class names (".Main") are expanded; anything else malformed gives null.
         */
        fun parseComponent(value: String): Pair<String, String>? {
            val inner = value.trim().removePrefix("ComponentInfo{").removeSuffix("}")
            val slash = inner.indexOf('/')
            if (slash <= 0 || slash == inner.length - 1) return null
            val pkg = inner.substring(0, slash).trim()
            val cls = inner.substring(slash + 1).trim()
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return pkg to if (cls.startsWith(".")) pkg + cls else cls
        }

        private fun key(packageName: String, className: String) = "$packageName/$className"
    }
}
