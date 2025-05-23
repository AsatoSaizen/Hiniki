package ani.dantotsu.addons

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import ani.dantotsu.addons.download.DownloadAddon
import ani.dantotsu.addons.download.DownloadAddonApiV2
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.addons.download.DownloadLoadResult
import ani.himitsu.media.AddonType
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.getApplicationIcon

class AddonLoader {
    companion object {

        /**
         * Load an add-on from a package name with a specific class name
         * @param context the context
         * @param packageName the package name of the add-on
         * @param type the type of add-on
         * @return the loaded add-on
         * @throws IllegalStateException if the add-on is not of the correct type
         * @throws ClassNotFoundException if the add-on class is not found
         * @throws NoClassDefFoundError if the add-on class is not found
         * @throws Exception if any other error occurs
         * @throws PackageManager.NameNotFoundException if the package is not found
         * @throws IllegalStateException if the add-on is not found
         */
        fun loadExtension(
            context: Context,
            packageName: String,
            className: String,
            type: AddonType
        ): LoadResult? {
            val pkgManager = context.packageManager

            val installedPkgs = if (Version.isTiramisu) {
                pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(ExtensionLoader.PACKAGE_FLAGS.toLong()))
            } else {
                pkgManager.getInstalledPackages(ExtensionLoader.PACKAGE_FLAGS)
            }

            val extPkgs = installedPkgs.filter {
                isPackageAnExtension(packageName, it)
            }

            if (extPkgs.isEmpty()) return null
            if (extPkgs.size > 1) throw IllegalStateException("Multiple extensions with the same package name found")

            val pkgName = extPkgs.first().packageName
            val pkgInfo = extPkgs.first()

            val appInfo = try {
                pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
            } catch (error: PackageManager.NameNotFoundException) {
                // Unlikely, but the package may have been uninstalled at this point
                Logger.log(error)
                throw error
            }

            val extName = pkgManager.getApplicationLabel(appInfo).toString()
                .substringAfter("Dantotsu: ")
            val versionName = pkgInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

            if (versionName.isNullOrEmpty()) {
                Logger.log("Missing versionName for addon $extName")
                throw IllegalStateException("Missing versionName for addon $extName")
            }
            val classLoader =
                PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
            val loadedClass = try {
                Class.forName(className, false, classLoader)
            } catch (e: Exception) {
                Logger.log(e)
                throw e
            }
            val instance = loadedClass.getDeclaredConstructor().newInstance()

            return when (type) {
                AddonType.DOWNLOAD -> {
                    val extension = instance as? DownloadAddonApiV2
                        ?: throw IllegalStateException("Addon is not a DownloadAddonApiV2")
                    DownloadLoadResult.Success(
                        DownloadAddon.Installed(
                            name = extName,
                            pkgName = pkgName,
                            versionName = versionName,
                            versionCode = versionCode,
                            extension = extension,
                            icon = context.getApplicationIcon(pkgName),
                        )
                    )
                }
            }
        }

        /**
         * Load an extension from a package name (class is determined by type)
         * @param context the context
         * @param packageName the package name of the extension
         * @param type the type of extension
         * @return the loaded extension
         */
        fun loadFromPkgName(context: Context, packageName: String, type: AddonType): LoadResult? {
            return try {
                when (type) {
                    AddonType.DOWNLOAD -> loadExtension(
                        context,
                        packageName,
                        DownloadAddonManager.DOWNLOAD_CLASS,
                        type
                    )
                }
            } catch (e: Exception) {
                Logger.log("Error loading extension from package name: $packageName")
                Logger.log(e)
                null
            }
        }

        /**
         * Check if a package is an extension by comparing the package name
         * @param type the type of extension
         * @param pkgInfo the package info
         * @return true if the package is an extension
         */
        private fun isPackageAnExtension(type: String, pkgInfo: PackageInfo): Boolean {
            return pkgInfo.packageName.equals(type)
        }
    }

}