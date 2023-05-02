package tech.sethi.pebbles.crates.lootcrates

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.util.math.BlockPos
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.Paths
import java.util.HashMap

class CrateDataManager {
    private val CRATE_DATA_FILE = Paths.get("config", "pebbles-crate", "crate_data.json").toString()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CRATE_DATA_TYPE: Type = object : TypeToken<Map<String, String>>() {}.type


    fun loadCrateData(): Map<BlockPos, String> {
        val crateData = HashMap<BlockPos, String>()

        try {
            FileReader(CRATE_DATA_FILE).use { reader ->
                val rawCrateData = GSON.fromJson<Map<String, String>>(reader, CRATE_DATA_TYPE) ?: return crateData
                for ((key, value) in rawCrateData) {
                    val pos = BlockPos.fromLong(key.toLong())
                    crateData[pos] = value
                }
            }
        } catch (e: IOException) {
            // File not found, assume no data yet
        }

        return crateData
    }

    fun saveCrateData(crateData: Map<BlockPos, String>) {
        val rawCrateData = HashMap<String, String>()

        for ((key, value) in crateData) {
            rawCrateData[key.asLong().toString()] = value
        }

        try {
            FileWriter(CRATE_DATA_FILE).use { writer ->
                GSON.toJson(rawCrateData, CRATE_DATA_TYPE, writer)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
