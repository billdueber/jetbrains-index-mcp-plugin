package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId

/**
 * Utility for detecting Rust plugin availability.
 *
 * Supports both the new official JetBrains Rust plugin and the
 * deprecated intellij-rust community plugin for maximum compatibility.
 *
 * This class caches the Rust plugin availability check to avoid repeated checks.
 * The check is performed once at class initialization and the result is cached
 * for the lifetime of the JVM.
 *
 * ## Why This Matters
 *
 * Many tools in this plugin can use Rust-specific PSI APIs (RsStructItem, RsTraitItem,
 * RsFunction, etc.) that are only available when a Rust plugin is installed. In non-Rust
 * IDEs, these classes don't exist and would cause NoClassDefFoundError.
 *
 * ## Usage
 *
 * ```kotlin
 * if (RustPluginDetector.isRustPluginAvailable) {
 *     // Safe to use Rust-specific APIs
 *     val rsFile = psiFile as? RsFile
 * }
 * ```
 *
 * ## IDE Compatibility
 *
 * | IDE | Rust Plugin Available |
 * |-----|----------------------|
 * | RustRover | Yes (bundled) |
 * | IntelliJ IDEA Ultimate | Yes (marketplace) |
 * | CLion | Yes (marketplace) |
 * | IntelliJ IDEA Community | Limited (deprecated plugin only) |
 * | PyCharm | No |
 * | GoLand | No |
 * | PhpStorm | No |
 * | WebStorm | No |
 *
 * ## Plugin IDs
 *
 * - `org.jetbrains.rust` - New official JetBrains Rust plugin (RustRover, IDEA Ultimate, CLion)
 * - `org.rust.lang` - Deprecated community intellij-rust plugin
 */
object RustPluginDetector {

    private val LOG = logger<RustPluginDetector>()

    /**
     * Plugin IDs for Rust support, in order of preference.
     * The new official plugin is checked first, with fallback to the deprecated community plugin.
     */
    private val RUST_PLUGIN_IDS = listOf(
        "org.jetbrains.rust",  // New official JetBrains plugin
        "org.rust.lang"        // Deprecated community plugin
    )

    /**
     * Cached result of Rust plugin availability check.
     *
     * This is computed once at class initialization using a lazy delegate
     * that is thread-safe by default (LazyThreadSafetyMode.SYNCHRONIZED).
     */
    val isRustPluginAvailable: Boolean by lazy {
        checkRustPluginAvailable()
    }

    /**
     * Performs the actual check for Rust plugin availability.
     * Checks both the new official plugin and the deprecated community plugin.
     */
    private fun checkRustPluginAvailable(): Boolean {
        for (pluginId in RUST_PLUGIN_IDS) {
            try {
                val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
                if (plugin != null && plugin.isEnabled) {
                    LOG.info("Rust plugin detected ($pluginId) - Rust-specific tools will be available")
                    return true
                }
            } catch (e: Exception) {
                LOG.debug("Failed to check Rust plugin $pluginId: ${e.message}")
            }
        }
        LOG.info("Rust plugin not available - Rust-specific features will be disabled")
        return false
    }

    /**
     * Executes the given block only if a Rust plugin is available.
     *
     * @param block The code block to execute if Rust plugin is available
     * @return The result of the block, or null if Rust plugin is not available
     */
    inline fun <T> ifRustAvailable(block: () -> T): T? {
        return if (isRustPluginAvailable) {
            block()
        } else {
            null
        }
    }

    /**
     * Executes the given block only if a Rust plugin is available,
     * otherwise returns the provided default value.
     *
     * @param default The default value to return if Rust plugin is not available
     * @param block The code block to execute if Rust plugin is available
     * @return The result of the block, or the default value if Rust plugin is not available
     */
    inline fun <T> ifRustAvailableOrElse(default: T, block: () -> T): T {
        return if (isRustPluginAvailable) {
            block()
        } else {
            default
        }
    }
}
