/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.extension.api

import ani.himitsu.asyncMap
import ani.himitsu.others.LanguageMapper
import ani.himitsu.parsers.novel.AvailableNovelSources
import ani.himitsu.parsers.novel.AvailablePluginSources
import ani.himitsu.parsers.novel.NovelExtension
import ani.himitsu.parsers.novel.NovelPlugin
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.nio.repoLong
import bit.himitsu.nio.repoNoRawOrNull
import bit.himitsu.webkit.isValid
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AvailableAnimeSources
import eu.kanade.tachiyomi.extension.manga.model.AvailableMangaSources
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.FileNotFoundException
import java.net.URL

internal class ExtensionGithubApi {
    private val networkService: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private fun List<ExtensionSourceJsonObject>.toAnimeExtensionSources(): List<AvailableAnimeSources> {
        return this.map {
            AvailableAnimeSources(
                id = it.id,
                lang = it.lang,
                name = it.name,
                baseUrl = it.baseUrl,
            )
        }
    }

    private fun List<ExtensionJsonObject>.toAnimeExtensions(repository: String): List<AnimeExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.ANIME_LIB_VERSION_MIN && libVersion <= ExtensionLoader.ANIME_LIB_VERSION_MAX
            }
            .map {
                AnimeExtension.Available(
                    name = it.name.substringAfter("Aniyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toAnimeExtensionSources().orEmpty(),
                    apkName = it.apk,
                    repository = repository,
                    iconUrl = "${repository}/icon/${it.pkg}.png",
                )
            }
    }

    suspend fun findAnimeExtensions(): List<AnimeExtension.Available> {
        return withIOContext {

            val extensions: ArrayList<AnimeExtension.Available> = arrayListOf()

            PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos).toMutableList().asyncMap {
                try {
                    val url = "${it.repoLong}/index.min.json"
                    val fallback = "${fallbackRepoUrl(it.repoLong)}/index.min.json"
                    if (!URL(url).isValid() && !URL(fallback).isValid()) {
                        throw FileNotFoundException("$url invalid!")
                    }

                    val githubResponse = try {
                        networkService.client.newCall(GET(url)).awaitSuccess()
                    } catch (e: Throwable) {
                        Logger.log("Failed to get repo: $it")
                        Logger.log(e)
                        null
                    }

                    val response = githubResponse ?: run {
                        networkService.client.newCall(GET(fallback)).awaitSuccess()
                    }

                    val repoExtensions = with(json) {
                        response
                            .parseAs<List<ExtensionJsonObject>>()
                            .toAnimeExtensions(it.repoLong)
                    }

                    // Sanity check - a small number of extensions probably means something broke
                    // with the repo generator
//                        if (repoExtensions.size < 10) {
//                            throw Exception()
//                        }

                    extensions.addAll(repoExtensions)
                } catch (e: Throwable) {
                    Logger.log("Failed to get extensions from GitHub")
                    if (e !is FileNotFoundException) Logger.log(e)
                }
            }
            extensions
        }
    }

    fun getAnimeApkUrl(extension: AnimeExtension.Available): String {
        return "${extension.repository}/apk/${extension.apkName}"
    }

    private fun List<ExtensionSourceJsonObject>.toMangaExtensionSources(): List<AvailableMangaSources> {
        return this.map {
            AvailableMangaSources(
                id = it.id,
                lang = it.lang,
                name = it.name,
                baseUrl = it.baseUrl,
            )
        }
    }

    private fun List<ExtensionJsonObject>.toMangaExtensions(repository: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.MANGA_LIB_VERSION_MIN
                        && libVersion <= ExtensionLoader.MANGA_LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toMangaExtensionSources().orEmpty(),
                    apkName = it.apk,
                    repository = repository,
                    iconUrl = "${repository}/icon/${it.pkg}.png",
                )
            }
    }

    suspend fun findMangaExtensions(): List<MangaExtension.Available> {
        return withIOContext {

            val extensions: ArrayList<MangaExtension.Available> = arrayListOf()

            PrefManager.getVal<Set<String>>(PrefName.MangaExtensionRepos).toMutableList().asyncMap {
                try {
                    val url = "${it.repoLong}/index.min.json"
                    val fallback = "${fallbackRepoUrl(it.repoLong)}/index.min.json"
                    if (!URL(url).isValid() && !URL(fallback).isValid()) {
                        throw FileNotFoundException("$url invalid!")
                    }

                    val response = try {
                        networkService.client.newCall(GET(url)).awaitSuccess()
                    } catch (e: Throwable) {
                        Logger.log("Failed to get repo: $it")
                        Logger.log(e)
                        null
                    } ?: run {
                        networkService.client.newCall(GET(fallback)).awaitSuccess()
                    }

                    val repoExtensions = with(json) {
                        response
                            .parseAs<List<ExtensionJsonObject>>()
                            .toMangaExtensions(it.repoLong)
                    }

                    extensions.addAll(repoExtensions)
                } catch (e: Throwable) {
                    Logger.log("Failed to get extensions from GitHub")
                    if (e !is FileNotFoundException) Logger.log(e)
                }
            }
            extensions
        }
    }

    fun getMangaApkUrl(extension: MangaExtension.Available): String {
        return "${extension.repository}/apk/${extension.apkName}"
    }

    private fun List<ExtensionSourceJsonObject>.toNovelSources(): List<AvailableNovelSources> {
        return this.map { source ->
            AvailableNovelSources(
                id = source.id,
                lang = source.lang,
                name = source.name,
                baseUrl = source.baseUrl,
            )
        }
    }

    private fun List<ExtensionJsonObject>.toNovelExtensions(repository: String): List<NovelExtension.Available> {
        return mapNotNull {
            NovelExtension.Available(
                name = it.name,
                pkgName = it.pkg,
                versionName = it.version,
                versionCode = it.code,
                sources = it.sources?.map { source ->
                    ExtensionSourceJsonObject(
                        id = source.id,
                        lang = source.lang,
                        name = source.name,
                        baseUrl = source.baseUrl,
                    )
                }?.toNovelSources().orEmpty(),
                repository = repository,
                iconUrl = "${repository}/icon/${it.pkg}.png",
            )
        }
    }

    suspend fun findNovelExtensions(): List<NovelExtension.Available> {
        return withIOContext {

            val extensions: ArrayList<NovelExtension.Available> = arrayListOf()

            PrefManager.getVal<Set<String>>(PrefName.NovelExtensionRepos).toMutableList().asyncMap {
                try {
                    val url = "${it.repoLong}/index.min.json"
                    val fallback = "${fallbackRepoUrl(it.repoLong)}/index.min.json"
                    if (!URL(url).isValid() && !URL(fallback).isValid()) {
                        throw FileNotFoundException("$url invalid!")
                    }

                    val response = try {
                        networkService.client.newCall(GET(url)).awaitSuccess()
                    } catch (e: Throwable) {
                        Logger.log("Failed to get repo: $it")
                        Logger.log(e)
                        null
                    } ?: run {
                        networkService.client.newCall(GET(fallback)).awaitSuccess()
                    }

                    val repoExtensions = with(json) {
                        response
                            .parseAs<List<ExtensionJsonObject>>()
                            .toNovelExtensions(it.repoLong)
                    }

                    extensions.addAll(repoExtensions)
                } catch (e: Throwable) {
                    if (e !is FileNotFoundException) Logger.log(e)
                }
            }
            extensions
        }
    }

    private fun List<ExtensionSourceJsonObject>.toPluginSources(): List<AvailablePluginSources> {
        return this.map { source ->
            AvailablePluginSources(
                id = source.id,
                lang = source.lang,
                name = source.name,
                baseUrl = source.baseUrl,
            )
        }
    }

    // https://github.com/LNReader/lnreader-plugins/blob/master/docs/plugin-template.ts
    private fun List<PluginJsonObject>.toNovelPlugins(repository: String): List<NovelPlugin.Available> {
        return this
//            .filter {
//                it.url.endsWith("[madara].js") || it.iconUrl.contains("/madara/")
//            }
            .map { extension ->
                val lang = LanguageMapper.mapNativeToCode(extension.lang) ?: "Multi"
                val sources =
                    listOf(
                        ExtensionSourceJsonObject(
                            extension.id.hashCode().toLong(),
                            lang,
                            extension.name,
                            extension.site,
                        ),
                        ExtensionSourceJsonObject(
                            extension.id.hashCode().toLong(),
                            lang,
                            extension.name,
                            extension.url,
                        )
                    )
                NovelPlugin.Available(
                    extension.name,
                    extension.id,
                    extension.version,
                    extension.version.replace(".", "").toLong(),
                    lang,
                    sources.toPluginSources(),
                    repository = repository,
                    iconUrl = extension.iconUrl,
                )
            }
    }

    suspend fun findNovelPlugins(): List<NovelPlugin.Available> {
        return withIOContext {

            val plugins: ArrayList<NovelPlugin.Available> = arrayListOf()

            PrefManager.getVal<Set<String>>(PrefName.NovelExtensionRepos).toMutableList().asyncMap {
                try {
                    val url = "${it.repoLong}/plugins.min.json"
                    if (!URL(url).isValid()) throw FileNotFoundException("$url invalid!")

                    val repoExtensions = with(json) {
                        networkService.client
                            .newCall(GET(url))
                            .awaitSuccess()
                            .parseAs<List<PluginJsonObject>>()
                            .toNovelPlugins(it.repoLong)
                    }

                    plugins.addAll(repoExtensions)
                } catch (ex: Throwable) {
                    if (ex !is FileNotFoundException) Logger.log(ex)
                }
            }
            plugins
        }
    }

    fun getNovelApkUrl(extension: NovelExtension.Available): String {
        return "${extension.repository}/apk/${extension.pkgName}.apk"
    }

    private fun fallbackRepoUrl(repoUrl: String): String? {
        var fallbackRepoUrl = "https://gcore.jsdelivr.net/gh/"
        val strippedRepoUrl = repoUrl.repoNoRawOrNull ?: return null
        val repoUrlParts = strippedRepoUrl.split("/")
        if (repoUrlParts.size < 3) return null
        val repoOwner = repoUrlParts[0]
        val repoName = repoUrlParts[1]
        fallbackRepoUrl += "$repoOwner/$repoName"
        val repoBranch = if (repoUrlParts.size == 4) {
            repoUrlParts[2]
        } else {
            "main"
        }
        fallbackRepoUrl += "@$repoBranch"
        return fallbackRepoUrl
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class PluginJsonObject(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: String,
    val url: String,
    val iconUrl: String,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private fun ExtensionJsonObject.extractLibVersion(): Double {
    return version.substringBeforeLast('.').toDouble()
}
