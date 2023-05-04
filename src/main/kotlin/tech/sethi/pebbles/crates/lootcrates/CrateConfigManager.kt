package tech.sethi.pebbles.crates.lootcrates

import ItemStackTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.item.ItemStack
import java.io.File

class CrateConfigManager {
    private val gson: Gson = GsonBuilder().registerTypeAdapter(ItemStack::class.java, ItemStackTypeAdapter()).create()
    private val configDirectory = File("config/pebbles-crate/crates")


    fun createCratesFolder() {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }
    }

    init {
        loadCrateConfigs()
    }

    fun getCrateConfig(crateName: String): CrateConfig? {
        loadCrateConfigs()
        return crateConfigs[crateName]
    }

    fun saveCrateConfigs(updatedCrateConfigs: List<CrateConfig>) {
        crateConfigs.clear()
        updatedCrateConfigs.forEach { crateConfig ->
            val crateName = crateConfig.crateName
            crateConfigs[crateName] = crateConfig
            val file = File(configDirectory, "$crateName.json")
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

        val loadedConfigs = mutableListOf<CrateConfig>()

        configDirectory.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            val json = file.readText()
            if (json.isNotEmpty()) {
                val crateConfig = gson.fromJson(json, CrateConfig::class.java)
                val crateName = crateConfig.crateName
                crateConfigs[crateName] = crateConfig
                loadedConfigs.add(crateConfig)
            }
        }

        return loadedConfigs
    }

    companion object {
        private val crateConfigs = mutableMapOf<String, CrateConfig>()
    }
}

data class CrateConfig(
    val crateName: String,
    val crateKey: CrateKey,
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
    val broadcast: String,
    val messageToOpener: String,
    val lore: List<String>?,
    val chance: Int
)
