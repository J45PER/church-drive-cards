package com.churchdrive.app.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hybrid scenes: one tap gives each light in a room its own look (decided 2026-11-20, kept in the house for everyone, made by
 * administrators). A look is one of [HYBRID_KINDS]: leave the light, switch it off, a white scene, a colour, a gradient or a dynamic scene.
 */
data class HybridLook(val kind: String, val value: String? = null, val colours: List<String> = emptyList())

/** A theme gives each role (see [LIGHT_ROLES]) a look, so it works whichever lights a room gives that role; [looks] is the older kind, one look per named light. */
data class Hybrid(val id: String, val name: String, val icon: String, val target: String, val looks: Map<String, HybridLook>, val roles: Map<String, HybridLook> = emptyMap())

/** What the house answered: every hybrid scene, and which one is playing on each room (by the room's group light). */
data class HybridList(
    val hybrids: List<Hybrid> = emptyList(),
    val active: Map<String, String> = emptyMap(),
    /** The lights that are Hue (they can play a dynamic scene) and the ones that can really do a gradient, which the other lights are not offered. */
    val hueLights: Set<String> = emptySet(),
    val gradientLights: Set<String> = emptySet(),
    /** The role each light has in themes ("ambient", "tertiary"); a light that isn't here is Primary. */
    val roles: Map<String, String> = emptyMap(),
) {
    fun roleOf(light: String): String = roles[light] ?: "primary"

    fun of(target: String): List<Hybrid> = hybrids.filter { it.target == target }
}

/** The roles a light can have in themes, in the order they are offered, with what each is for. */
val LIGHT_ROLES = listOf("primary", "ambient", "tertiary")

fun lightRoleLabel(role: String): String = when (role) { "ambient" -> "Ambient"; "tertiary" -> "Tertiary"; else -> "Primary" }

fun lightRoleHint(role: String): String = when (role) {
    "ambient" -> "Rings, strips and lamps that set the mood"
    "tertiary" -> "Anything else a room needs, such as a fitting's other lights"
    else -> "The main light of the room"
}

/** The looks a role can be given: the main light a white or a colour, the others a colour, a gradient and (for ambient) a dynamic scene. */
fun hybridKindsForRole(role: String): List<String> = when (role) {
    "ambient" -> listOf("leave", "off", "colour", "gradient", "dynamic")
    "tertiary" -> listOf("leave", "off", "colour", "gradient")
    else -> listOf("leave", "off", "white", "colour")
}

val HYBRID_KINDS = listOf("leave", "off", "white", "colour", "gradient", "dynamic")

fun hybridKindLabel(kind: String): String = when (kind) {
    "leave" -> "Leave"; "off" -> "Off"; "white" -> "White scene"; "colour" -> "Colour"; "gradient" -> "Gradient"; "dynamic" -> "Dynamic"
    else -> kind
}

fun hybridKindIcon(kind: String): String = when (kind) {
    "leave" -> "cd:minus"; "off" -> "cd:power"; "white" -> "cd:sun"; "colour" -> "cd:palette"; "gradient" -> "cd:bulb-group"; else -> "cd:animation-play"
}

/** The icons a hybrid scene can have: the scene icons, and the ones other sections' scenes use. The house keeps the same list. */
val HYBRID_ICONS = listOf(
    "cd:sun", "cd:sun-half", "cd:flower", "cd:moon", "cd:bolt", "cd:palette", "cd:animation-play", "cd:bulb-lit", "cd:room-bed-double",
    "cd:weather-sunset", "cd:television", "cd:music-note", "cd:leaf", "cd:fire", "cd:snowflake", "cd:home", "cd:home-away", "cd:shield-moon",
)

/** Colour presets for a gradient look: three colours each, as the dashboards' gradients. */
val HYBRID_GRADIENTS = listOf(
    "Sunset" to listOf("#ff4d4d", "#ff9a3c", "#ffd84a"),
    "Ocean" to listOf("#3ec6ff", "#5b8cfd", "#b36bff"),
    "Forest" to listOf("#4cc38a", "#ffd84a", "#3ec6ff"),
    "Rose" to listOf("#ff6bbd", "#b36bff", "#5b8cfd"),
)

/** The looks a light can be given: each only offers what it can do. */
fun hybridKindsFor(kind: LightKind, canGradient: Boolean = true, canDynamic: Boolean = true): List<String> = when (kind) {
    LightKind.Switch -> listOf("leave", "off")
    LightKind.Dim, LightKind.White -> listOf("leave", "off", "white")
    LightKind.Colour -> HYBRID_KINDS.filter { (it != "gradient" || canGradient) && (it != "dynamic" || canDynamic) }
}

/** What a new look of [kind] starts as. */
fun hybridDefaultLook(kind: String): HybridLook = when (kind) {
    "white" -> HybridLook("white", WHITE_SCENES.first())
    "colour" -> HybridLook("colour", "#ff9a3c")
    "gradient" -> HybridLook("gradient", colours = HYBRID_GRADIENTS[1].second)
    "dynamic" -> HybridLook("dynamic", "soho")
    else -> HybridLook(kind)
}

private fun parseLook(o: JSONObject?): HybridLook? {
    o ?: return null
    val kind = o.optString("t")
    if (kind !in HYBRID_KINDS) return null
    return when (kind) {
        "white", "dynamic", "colour" -> HybridLook(kind, o.optString("v").takeIf { it.isNotBlank() } ?: return null)
        "gradient" -> {
            val a = o.optJSONArray("v") ?: return null
            HybridLook(kind, colours = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.takeIf { it.size >= 2 } ?: return null)
        }
        else -> HybridLook(kind)
    }
}

fun parseHybrid(o: JSONObject?): Hybrid? {
    o ?: return null
    val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
    val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
    val target = o.optString("target").takeIf { it.isNotBlank() } ?: return null
    val looksJson = o.optJSONObject("looks")
    val looks = looksJson?.keys()?.asSequence()?.mapNotNull { k -> parseLook(looksJson.optJSONObject(k))?.let { k to it } }?.toMap().orEmpty()
    val rolesJson = o.optJSONObject("roles")
    val roles = rolesJson?.keys()?.asSequence()?.filter { it in LIGHT_ROLES }?.mapNotNull { k -> parseLook(rolesJson.optJSONObject(k))?.let { k to it } }?.toMap().orEmpty()
    if (looks.isEmpty() && roles.isEmpty()) return null
    return Hybrid(id, name, o.optString("icon").takeIf { it in HYBRID_ICONS } ?: "cd:palette", target, looks, roles)
}

/** The house's answer to `church_drive/hybrids`, or null if it didn't answer. */
fun parseHybrids(result: Any?): HybridList? {
    val o = result as? JSONObject ?: return null
    val list = o.optJSONArray("hybrids") ?: JSONArray()
    val hybrids = (0 until list.length()).mapNotNull { parseHybrid(list.optJSONObject(it)) }
    val activeJson = o.optJSONObject("active")
    val active = activeJson?.keys()?.asSequence()?.associateWith { activeJson.optString(it) } ?: emptyMap()
    val can = o.optJSONObject("can")
    fun ids(key: String): Set<String> = can?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
    val rolesJson = o.optJSONObject("roles")
    val roles = rolesJson?.keys()?.asSequence()?.associateWith { rolesJson.optString(it) }?.filterValues { it in LIGHT_ROLES }.orEmpty()
    return HybridList(hybrids, active, ids("hue"), ids("gradient"), roles)
}

/** A "#rrggbb" colour as hue (0 to 360) and saturation (0 to 100), the pair the colour wheel and the saved colours use; null if it isn't a colour. */
fun hexToHs(hex: String): Pair<Double, Double>? {
    val argb = hexToArgb(hex) ?: return null
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    val hue = when {
        d == 0.0 -> 0.0
        max == r -> 60.0 * (((g - b) / d) % 6.0)
        max == g -> 60.0 * ((b - r) / d + 2.0)
        else -> 60.0 * ((r - g) / d + 4.0)
    }
    return ((hue + 360.0) % 360.0) to (if (max == 0.0) 0.0 else d / max * 100.0)
}

/** The fewest and most colours a gradient look can have. */
const val MIN_GRADIENT_POINTS = 2
const val MAX_GRADIENT_POINTS = 5

/** What a hybrid is being made or changed into: [id] is null for a new one. */
data class HybridDraft(val id: String?, val name: String, val icon: String, val looks: Map<String, HybridLook>, val roles: Map<String, HybridLook> = emptyMap())

fun HybridDraft.toHybrid(target: String) = Hybrid(id ?: "", name, icon, target, looks, roles)

/** The body of `church_drive/hybrid/save` for [draft] in the room [target]. */
fun hybridSaveBody(draft: HybridDraft, target: String): JSONObject {
    fun lookJson(look: HybridLook) = JSONObject().put("t", look.kind).apply {
        when (look.kind) {
            "white", "dynamic", "colour" -> put("v", look.value ?: "")
            "gradient" -> put("v", JSONArray(look.colours))
        }
    }
    val looks = JSONObject()
    draft.looks.forEach { (entity, look) -> looks.put(entity, lookJson(look)) }
    val roles = JSONObject()
    draft.roles.forEach { (role, look) -> roles.put(role, lookJson(look)) }
    val hybrid = JSONObject().put("name", draft.name.trim()).put("icon", draft.icon).put("target", target).put("looks", looks).put("roles", roles)
    draft.id?.let { hybrid.put("id", it) }
    return JSONObject().put("hybrid", hybrid)
}

/** Why a draft can't be saved yet, or null. */
fun hybridProblem(draft: HybridDraft): String? = when {
    draft.name.isBlank() -> "Give it a name."
    (draft.looks.values + draft.roles.values).none { it.kind != "leave" } -> "Every light is set to Leave, so this would do nothing."
    else -> null
}

/** "#ff9a3c" as an opaque ARGB colour, or null if it isn't a hex colour. */
fun hexToArgb(hex: String): Int? {
    val v = hex.removePrefix("#")
    if (v.length != 6) return null
    val n = v.toIntOrNull(16) ?: return null
    return (0xFF shl 24) or n
}

/** A hybrid's tile: the colours of its first two lights that do something, as a hard split (one colour if only one does). */
fun hybridTileColours(hybrid: Hybrid, looks: SceneLooks): List<Int> {
    val cs = (hybrid.roles.values + hybrid.looks.values).mapNotNull { look ->
        when (look.kind) {
            "colour" -> look.value?.let(::hexToArgb)
            "gradient" -> look.colours.firstOrNull()?.let(::hexToArgb)
            "white" -> look.value?.let { looks.whiteColours(it, it)?.firstOrNull() ?: looks.colours(it, it)?.firstOrNull() }
            "dynamic" -> look.value?.let { looks.colours(it, it)?.firstOrNull() }
            else -> null
        }
    }
    val a = cs.firstOrNull() ?: 0xFF6B7280.toInt()
    val b = cs.firstOrNull { it != a } ?: a
    return listOf(a, b)
}

/** What a look says in a list: "White scene · Relax", "Dynamic · Soho", "Colour", "Off". */
fun hybridLookText(look: HybridLook, sceneName: (String) -> String): String = when (look.kind) {
    "white", "dynamic" -> "${hybridKindLabel(look.kind)} · ${look.value?.let(sceneName) ?: ""}"
    else -> hybridKindLabel(look.kind)
}
