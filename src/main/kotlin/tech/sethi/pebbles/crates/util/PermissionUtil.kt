package tech.sethi.pebbles.crates.util

import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.util.Tristate
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.ServerCommandSource

/**
 * The single admin gate for the mod: /padmin, turning a block into a crate and removing one all
 * ask the same question here.
 *
 * LuckPerms is an optional dependency - without it an operator (permission level 2) is the admin.
 * With it, an unloaded user answers "not an admin" instead of throwing.
 */
object PermissionUtil {
    const val ADMIN_PERMISSION = "pebbles.admin.crate"

    /**
     * The player-facing /keys commands. Unlike the admin node this one is granted by default -
     * a wallet nobody can look at is not useful - so it is only ever a way to take /keys away.
     */
    const val KEYS_PERMISSION = "pebbles.crate.keys"

    private val luckPermsPresent: Boolean by lazy {
        try {
            Class.forName("net.luckperms.api.LuckPerms")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun luckPermsApi(): LuckPerms? {
        if (!luckPermsPresent) return null
        return try {
            LuckPermsProvider.get()
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun hasLuckPermsPermission(player: PlayerEntity, permission: String): Boolean {
        val user = luckPermsApi()?.userManager?.getUser(player.uuid) ?: return false
        return user.cachedData.permissionData.checkPermission(permission).asBoolean()
    }

    fun isAdmin(player: PlayerEntity): Boolean =
        player.hasPermissionLevel(2) || hasLuckPermsPermission(player, ADMIN_PERMISSION)

    /** Console and command blocks keep the access they have always had. */
    fun isAdmin(source: ServerCommandSource): Boolean {
        val player = source.player ?: return source.entity == null
        return source.hasPermissionLevel(2) || hasLuckPermsPermission(player, ADMIN_PERMISSION)
    }

    /**
     * Default-allow: only an explicitly negated LuckPerms node takes /keys away. Asking the usual
     * way would deny it to every server that has LuckPerms installed but has not heard of the node.
     */
    fun canUseKeys(source: ServerCommandSource): Boolean {
        val player = source.player ?: return true
        val user = luckPermsApi()?.userManager?.getUser(player.uuid) ?: return true
        return user.cachedData.permissionData.checkPermission(KEYS_PERMISSION) != Tristate.FALSE
    }
}
