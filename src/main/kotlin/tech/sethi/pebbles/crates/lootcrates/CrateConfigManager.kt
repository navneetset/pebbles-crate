package tech.sethi.pebbles.crates.lootcrates

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.PebblesCrate
import java.io.File

object CrateConfigManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val gson: Gson = GsonBuilder().create()
    private val configDirectory = File("config/pebbles-crate/crates")
    private val crateConfigs = LinkedHashMap<String, CrateConfig>()

    /** Characters that are illegal in a Windows filename, plus the path separators. */
    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

    private val PLACEHOLDER = Regex("""\{[A-Za-z0-9_.]+}""")
    private val KNOWN_PLACEHOLDERS = setOf("{prize_name}", "{player_name}", "{crate_name}", "{chance}")

    fun createCratesFolder() {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }
    }

    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) loadCrateConfigs()
    }

    fun getCrateConfig(crateName: String): CrateConfig? {
        ensureLoaded()
        return crateConfigs[crateName]
    }

    /** Cached view of every loaded crate, in load order. Never touches disk. */
    fun getCrateConfigs(): List<CrateConfig> {
        ensureLoaded()
        return crateConfigs.values.toList()
    }

    fun getCrateNames(): List<String> {
        ensureLoaded()
        return crateConfigs.keys.toList()
    }

    fun saveCrateConfigs(updatedCrateConfigs: List<CrateConfig>) {
        crateConfigs.clear()
        updatedCrateConfigs.forEach { crateConfig ->
            val crateName = crateConfig.crateName
            crateConfigs[crateName] = crateConfig
            val file = File(configDirectory, "${sanitizeFileName(crateName)}.json")
            file.writeText(gson.toJson(crateConfig))
        }
    }

    fun setCrateConfig(crateName: String, crateConfig: CrateConfig) {
        crateConfigs[crateName] = crateConfig
        saveCrateConfigs(crateConfigs.values.toList())
    }

    fun loadCrateConfigs(): MutableList<CrateConfig> {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }

        val sourceFiles = mutableMapOf<String, String>()

        crateConfigs.clear()
        loaded = true

        // Sorted so that a duplicate crateName always resolves to the same winner across restarts.
        configDirectory.listFiles { _, name -> name.endsWith(".json") }?.sortedBy { it.name }?.forEach { file ->
            val json = try {
                file.readText()
            } catch (e: Exception) {
                logger.warn("[Pebbles-Crates] Could not read ${file.name}: ${e.message}")
                return@forEach
            }
            if (json.isBlank()) return@forEach

            val crateConfig = try {
                gson.fromJson(json, CrateConfig::class.java)
            } catch (e: Exception) {
                logger.warn("[Pebbles-Crates] ${file.name} is not valid crate JSON, skipping it: ${e.message}")
                return@forEach
            }

            @Suppress("SENSELESS_COMPARISON") // gson happily produces nulls for missing non-null fields
            if (crateConfig == null || crateConfig.crateName == null || crateConfig.crateKey == null || crateConfig.prize == null) {
                logger.warn("[Pebbles-Crates] ${file.name} is missing crateName, crateKey or prize, skipping it")
                return@forEach
            }

            val repairedConfig = repairNulls(crateConfig, file.name) ?: return@forEach

            val crateName = repairedConfig.crateName
            val existingSource = sourceFiles[crateName]
            if (existingSource != null) {
                // Last file alphabetically wins, which is what the previous overwrite-on-load did.
                logger.warn(
                    "[Pebbles-Crates] Duplicate crateName '$crateName' in $existingSource and ${file.name}; " + "using ${file.name}"
                )
            }

            sourceFiles[crateName] = file.name
            crateConfigs[crateName] = repairedConfig
        }

        val loadedConfigs = crateConfigs.values.toMutableList()

        // Item ids can only be checked once the registries are populated, i.e. from server start onwards.
        if (PebblesCrate.server != null) {
            loadedConfigs.forEach { validate(it, sourceFiles[it.crateName] ?: "${it.crateName}.json") }
        }

        return loadedConfigs
    }

    /**
     * Gson bypasses Kotlin constructors, so fields declared non-null can still come back null from a
     * hand-edited file (e.g. a crateKey with no lore used to NPE in CrateTransformer). Repairs what is
     * benign, skips what is unusable, warns either way.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun repairNulls(crateConfig: CrateConfig, fileName: String): CrateConfig? {
        val key = crateConfig.crateKey
        if (key.material == null || key.name == null) {
            logger.warn("[Pebbles-Crates] $fileName crateKey is missing material or name, skipping it")
            return null
        }
        val repairedKey = if (key.lore == null) {
            logger.warn("[Pebbles-Crates] $fileName crateKey has no lore, using an empty one")
            key.copy(lore = emptyList())
        } else key

        val prizes = (crateConfig.prize ?: emptyList()).mapNotNull { prize ->
            when {
                prize == null || prize.name == null || prize.material == null -> {
                    logger.warn("[Pebbles-Crates] $fileName has a prize missing name or material, skipping that prize")
                    null
                }

                prize.commands == null -> {
                    logger.warn("[Pebbles-Crates] $fileName prize '${prize.name}' has no commands and will award nothing")
                    prize.copy(commands = emptyList())
                }

                else -> prize
            }
        }

        return crateConfig.copy(crateKey = repairedKey, prize = prizes)
    }

    /** Logs anything that would silently misbehave at runtime. Never throws - a bad crate must not kill the server. */
    private fun validate(crateConfig: CrateConfig, fileName: String) {
        if (!itemExists(crateConfig.crateKey.material)) {
            logger.warn(
                "[Pebbles-Crates] $fileName: crate key material '${crateConfig.crateKey.material}' " + "does not resolve to an item, the key will be air"
            )
        }

        if (crateConfig.prize.isEmpty()) {
            logger.warn("[Pebbles-Crates] $fileName: crate '${crateConfig.crateName}' has no prizes, it cannot be opened")
            return
        }

        val totalWeight = crateConfig.prize.sumOf { it.chance }
        if (totalWeight <= 0) {
            logger.warn(
                "[Pebbles-Crates] $fileName: crate '${crateConfig.crateName}' has a total prize chance of " + "$totalWeight, it cannot be opened - give at least one prize a positive chance"
            )
        }

        // `{prize.name}` instead of `{prize_name}` is the classic one: it renders literally in chat
        // and there is no other sign anything is wrong.
        val unknownPlaceholders = crateConfig.prize.flatMap { prize ->
            (prize.lore.orEmpty() + listOfNotNull(prize.name, prize.broadcast, prize.messageToOpener))
        }.flatMap { PLACEHOLDER.findAll(it).map { match -> match.value } }.distinct()
            .filterNot { it in KNOWN_PLACEHOLDERS }

        if (unknownPlaceholders.isNotEmpty()) {
            logger.warn(
                "[Pebbles-Crates] $fileName: unknown placeholder(s) ${unknownPlaceholders.joinToString(", ")} " + "will be shown as written; supported are ${
                    KNOWN_PLACEHOLDERS.joinToString(", ")
                }"
            )
        }

        // Reported per distinct material - a crate built around a mod that is not installed would
        // otherwise log hundreds of near-identical lines.
        val unknownMaterials = crateConfig.prize.filterNot { itemExists(it.material) }.map { it.material }.distinct()
        if (unknownMaterials.isNotEmpty()) {
            logger.warn(
                "[Pebbles-Crates] $fileName: ${unknownMaterials.size} prize material(s) do not resolve to an item " + "and will show as air: ${
                    unknownMaterials.joinToString(", ")
                }"
            )
        }
    }

    private fun itemExists(material: String?): Boolean {
        val identifier = material?.let { Identifier.tryParse(it) } ?: return false
        return Registries.ITEM.containsId(identifier)
    }

    /** Keeps a crateName from escaping the crates folder or producing an unwritable filename. */
    private fun sanitizeFileName(crateName: String): String {
        val sanitized = ILLEGAL_FILENAME_CHARS.replace(crateName, "_").trim().trimEnd('.')
        return if (sanitized.isEmpty()) "unnamed_crate" else sanitized
    }
}

data class CrateConfig(
    val crateName: String,
    val crateKey: CrateKey,
    val screenName: String? = null,
    /**
     * Per-crate override for virtual keys. Absent (null) inherits the global switch in config.json,
     * which is what keeps every crate file written before this feature existed valid as-is.
     */
    val virtualKey: Boolean? = null,
    var prize: List<Prize>,
)

data class CrateKey(
    val material: String, val name: String, val nbt: String?, val lore: List<String>
)

data class Prize(
    val name: String,
    val material: String,
    val amount: Int,
    val nbt: String? = null,
    val commands: List<String>,
    val broadcast: String? = null,
    val messageToOpener: String? = null,
    val lore: List<String>?,
    val chance: Int
)
