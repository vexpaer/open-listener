package io.github.vexpaer.openlistener.security

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CloudProfile(val id: String, val kind: String, val provider: String, val label: String)

/**
 * Encrypted cloud configuration catalog. Every profile owns an isolated credential namespace.
 * Activating one copies its fields into the legacy provider namespace used by existing engines.
 * Existing credentials are imported once on first read, without exposing secrets in DataStore.
 */
class CloudProfileStore(private val credentials: CredentialsStore) {
    private val fields = listOf("api_key", "app_id", "access_key", "resource_id", "base_url", "model")
    private fun canonical(kind: String, provider: String): String =
        if (kind == "asr") {
            if (provider == "doubao" || provider == "doubao_ast") provider else "asr_$provider"
        } else "translation_$provider"

    @Synchronized fun list(kind: String): List<CloudProfile> {
        require(kind == "asr" || kind == "translation")
        val stored = credentials.get("cloud_profiles", kind)
        if (stored == null) {
            val providers = if (kind == "asr") listOf("doubao", "doubao_ast", "qwen", "openai", "custom")
                else listOf("doubao", "qwen", "openai", "custom")
            val existing = providers.mapNotNull { provider ->
                val key = canonical(kind, provider)
                if (credentials.get(key, "api_key").isNullOrBlank() &&
                    (credentials.get(key, "app_id").isNullOrBlank() ||
                     credentials.get(key, "access_key").isNullOrBlank())) null
                else {
                    val id = UUID.randomUUID().toString()
                    fields.forEach { field -> credentials.get(key, field)?.let {
                        credentials.set("cloud_profile_$id", field, it)
                    } }
                    CloudProfile(id, kind, provider, title(provider, credentials.get(key, "model")))
                }
            }
            persist(kind, existing)
            return existing
        }
        val json = JSONArray(stored)
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val provider = item.optString("provider")
            if (id.isBlank() || provider !in allowed(kind)) null
            else CloudProfile(id, kind, provider, item.optString("label", provider))
        }
    }
    private fun allowed(kind: String) = if (kind == "asr")
        setOf("doubao", "doubao_ast", "qwen", "openai", "custom")
        else setOf("doubao", "qwen", "openai", "custom")
    private fun title(provider: String, model: String?) =
        provider.replaceFirstChar { it.uppercase() } + model?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()

    @Synchronized fun add(kind: String, provider: String, values: Map<String, String>): CloudProfile {
        require(provider in allowed(kind))
        val previous = list(kind)
        val id = UUID.randomUUID().toString()
        fields.forEach { field -> credentials.set("cloud_profile_$id", field, values[field].orEmpty()) }
        val entry = CloudProfile(id, kind, provider, title(provider, values["model"]))
        persist(kind, previous + entry)
        activate(entry)
        return entry
    }
    @Synchronized fun activate(entry: CloudProfile) {
        require(list(entry.kind).any { it.id == entry.id && it.provider == entry.provider })
        val canonical = canonical(entry.kind, entry.provider)
        fields.forEach { field ->
            credentials.set(canonical, field, credentials.get("cloud_profile_${entry.id}", field).orEmpty())
        }
        credentials.set("cloud_profiles", "active_${entry.kind}", entry.id)
    }
    @Synchronized fun active(kind: String): String? = credentials.get("cloud_profiles", "active_$kind")
    @Synchronized fun update(entry: CloudProfile, values: Map<String, String>) {
        val entries = list(entry.kind)
        require(entries.any { it.id == entry.id })
        fields.forEach { field -> values[field]?.let { credentials.set("cloud_profile_${entry.id}", field, it) } }
        persist(entry.kind, entries.map {
            if (it.id == entry.id) it.copy(label = title(it.provider, values["model"])) else it
        })
        activate(entry)
    }
    /** Remove a profile and its encrypted secrets; restore a remaining valid selection. */
    @Synchronized fun delete(entry: CloudProfile): CloudProfile? {
        val existing = list(entry.kind)
        require(existing.any { it.id == entry.id && it.provider == entry.provider })
        val remaining = existing.filterNot { it.id == entry.id }
        val wasActive = active(entry.kind) == entry.id
        persist(entry.kind, remaining)
        fields.forEach { field -> credentials.remove("cloud_profile_${entry.id}", field) }
        if (!wasActive) return remaining.find { it.id == active(entry.kind) }
        credentials.remove("cloud_profiles", "active_${entry.kind}")
        // The legacy canonical fields also feed the engines. Never leave revoked API keys there.
        val canonicalKey = canonical(entry.kind, entry.provider)
        fields.forEach { field -> credentials.remove(canonicalKey, field) }
        return remaining.firstOrNull()?.also { activate(it) }
    }

    private fun persist(kind: String, entries: List<CloudProfile>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("id", it.id).put("provider", it.provider).put("label", it.label)) }
        credentials.set("cloud_profiles", kind, array.toString())
    }
}
