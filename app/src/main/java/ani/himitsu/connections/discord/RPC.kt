/*
 * https://discord.com/developers/docs/topics/rpc
 * Copyright © 2023 Kizzy. All rights reserved. (?)
 * https://github.com/LuftVerbot/kuukiyomi/pull/123
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ani.himitsu.connections.discord

import ani.himitsu.R
import ani.himitsu.connections.discord.Discord.discordUri
import ani.himitsu.connections.discord.serializers.Activity
import ani.himitsu.connections.discord.serializers.Presence
import ani.himitsu.media.cereal.Media
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.nio.Strings.getString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Suppress("MemberVisibilityCanBePrivate")
open class RPC {

    enum class Type {
        PLAYING, STREAMING, LISTENING, WATCHING, UNKNOWN, COMPETING
    }

    data class Link(val label: String, val url: String)

    @Serializable
    data class Asset(
        @SerialName("external_asset_path")
        val externalAssetPath: String? = null
    )

    companion object {
        const val iconAniList = "https://avatars.githubusercontent.com/u/18018524?s=200&v=4"
        const val loopHimitsu = "https://repodevil.netlify.app/himitsu_loop.gif"
        const val drawnHimitsu = "https://repodevil.netlify.app/himitsu_drawn.gif"
        val iconHimitsu = if (PrefManager.getVal<Boolean>(PrefName.LoopAnimatedRPC))
            loopHimitsu
        else
            drawnHimitsu

        var isEnabled = false

        const val INTENT_EXTRA = "presence"
        const val DURATION_EXTRA = "duration"
        const val CHANNEL_NAME = "discordPresence"

        data class RPCData(
            val applicationId: String = Discord.application_Id,
            val type: Type? = null,
            val activityName: String? = null,
            val details: String? = null,
            val state: String? = null,
            val largeImage: Link? = null,
            val smallImage: Link? = null,
            val status: String? = null,
            val startTimestamp: Long? = null,
            val stopTimestamp: Long? = null,
            val buttons: MutableList<Link> = mutableListOf()
        )

        suspend fun createPresence(data: RPCData): String {
            return Discord.json.encodeToString(Presence.Response(
                3,
                Presence(
                    activities = listOf(
                        Activity(
                            name = data.activityName,
                            state = data.state,
                            details = data.details,
                            type = data.type?.ordinal,
                            timestamps = if (data.startTimestamp != null)
                                Activity.Timestamps(data.startTimestamp, data.stopTimestamp)
                            else null,
                            assets = Activity.Assets(
                                largeImage = data.largeImage?.url?.discordUri(),
                                largeText = data.largeImage?.label,
                                smallImage = if (PrefManager.getVal(PrefName.ShowAniListIcon))
                                    iconAniList.discordUri()
                                else
                                    iconHimitsu.discordUri(),
                                smallText = if (PrefManager.getVal(PrefName.ShowAniListIcon))
                                    getString(R.string.anilist)
                                else
                                    getString(R.string.app_name),
                            ),
                            buttons = data.buttons.map { it.label },
                            metadata = Activity.Metadata(
                                buttonUrls = data.buttons.map { it.url }
                            ),
                            applicationId = data.applicationId,
                        )
                    ),
                    afk = true,
                    since = data.startTimestamp,
                    status = PrefManager.getVal(PrefName.DiscordStatus)
                )
            ))
        }

        fun getAnimeButtons(media: Media): MutableList<Link> {
            val discordMode = PrefManager.getCustomVal(
                "discord_mode", Discord.MODE.HIMITSU.name
            ).uppercase()
            return when (discordMode) {
                Discord.MODE.NOTHING.name -> mutableListOf(
                    Link(getString(R.string.rpc_anime), media.shareLink ?: "")
                )

                Discord.MODE.HIMITSU.name -> mutableListOf(
                    Link(getString(R.string.rpc_anime), media.shareLink ?: ""),
                    Link(
                        getString(R.string.rpc_watch),
                        getString(R.string.rpc_intent, media.shareLink?.removePrefix("https://anilist.co/"))
                    )
                )

                Discord.MODE.ANILIST.name -> {
                    val username = PrefManager.getVal<String>(PrefName.AnilistUserName)
                    val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                    mutableListOf(
                        Link(getString(R.string.rpc_anime), media.shareLink ?: ""),
                        Link(getString(R.string.rpc_anilist, username), "https://anilist.co/user/$userId/")
                    )
                }

                else -> mutableListOf()
            }
        }

        fun getMangaButtons(media: Media): MutableList<Link> {
            val discordMode = PrefManager.getCustomVal(
                "discord_mode", Discord.MODE.HIMITSU.name
            ).uppercase()
            return when (discordMode) {
                Discord.MODE.NOTHING.name -> mutableListOf(
                    Link(getString(R.string.rpc_manga), media.shareLink ?: "")
                )

                Discord.MODE.HIMITSU.name -> mutableListOf(
                    Link(getString(R.string.rpc_manga), media.shareLink ?: ""),
                    Link(
                        getString(R.string.rpc_read),
                        getString(R.string.rpc_intent, media.shareLink?.removePrefix("https://anilist.co/"))
                    )
                )

                Discord.MODE.ANILIST.name -> {
                    val username = PrefManager.getVal<String>(PrefName.AnilistUserName)
                    val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                    mutableListOf(
                        Link(getString(R.string.rpc_manga), media.shareLink ?: ""),
                        Link(getString(R.string.rpc_anilist, username), "https://anilist.co/user/$userId/")
                    )
                }

                else -> mutableListOf()
            }
        }

        fun getStreamButtons(url: String, media: Media, name: String): MutableList<Link> {
            val discordMode = PrefManager.getCustomVal(
                "discord_mode", Discord.MODE.HIMITSU.name
            ).uppercase()
            return when (discordMode) {
                Discord.MODE.NOTHING.name -> mutableListOf(
                    Link(getString(R.string.rpc_stream, name), url)
                )

                Discord.MODE.HIMITSU.name -> mutableListOf(
                    Link(getString(R.string.rpc_stream, name), url),
                    Link(
                        getString(R.string.rpc_watch),
                        getString(R.string.rpc_intent, media.shareLink?.removePrefix("https://anilist.co/"))
                    )
                )

                Discord.MODE.ANILIST.name -> {
                    val username = PrefManager.getVal<String>(PrefName.AnilistUserName)
                    val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                    mutableListOf(
                        Link(getString(R.string.rpc_stream, name), url),
                        Link(getString(R.string.rpc_anilist, username), "https://anilist.co/user/$userId/")
                    )
                }

                else -> mutableListOf()
            }
        }
    }
}


