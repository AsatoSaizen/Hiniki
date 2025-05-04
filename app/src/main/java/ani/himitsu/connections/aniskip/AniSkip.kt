/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * https://github.com/aniyomiorg/aniyomi/pull/772
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ani.himitsu.connections.aniskip

import android.content.Context
import ani.himitsu.R
import ani.himitsu.client
import ani.himitsu.connections.aniskip.AniSkip.Interval
import ani.himitsu.tryWithSuspend
import ani.himitsu.util.Logger
import bit.himitsu.nio.utf8
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

object AniSkip {

    suspend fun getResultV1(
        malId: Int,
        episodeNumber: Int,
        useProxyForTimeStamps: Boolean = false
    ): List<Stamp>? {
        val url = "https://api.aniskip.com/skip-times/$malId/$episodeNumber?types[]=ed&types[]=op"
        return tryWithSuspend {
            val res = client.get(if (useProxyForTimeStamps) {
                "https://corsproxy.io/?url=${url.utf8.replace("+", "%20")}"
            } else {
                url
            })
            val json = try {
                res.parsed<ResponseV1>()
            } catch (e: Exception) {
                Logger.log(e)
                null
            }
            when {
                (res.code == 525) && !useProxyForTimeStamps -> {
                    return@tryWithSuspend getResultV1(malId, episodeNumber, true)
                }
                json?.found == true -> arrayListOf<Stamp>().apply {
                    json.results?.forEach {
                        add(
                            Stamp(
                                Interval(it.interval.startTime, it.interval.endTime),
                                it.skipType,
                                it.skipId,
                                it.episodeLength
                            )
                        )
                    }
                }
                else -> { null }
            }
        }
    }

    suspend fun getResult(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long,
        useProxyForTimeStamps: Boolean = false
    ): List<Stamp>? {
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        return tryWithSuspend {
            val res = client.get(if (useProxyForTimeStamps) {
                "https://corsproxy.io/?url=${url.utf8.replace("+", "%20")}"
            } else {
                url
            })
            val json = try {
                res.parsed<Response>()
            } catch (e: Exception) {
                Logger.log(e)
                null
            }
            when {
                (res.code == 525) && !useProxyForTimeStamps -> {
                    return@tryWithSuspend getResult(malId, episodeNumber, episodeLength, true)
                }
                json?.found == true -> { json.results }
                else -> { null }
            }
        }
    }

    @Serializable
    data class ResponseV1(
        val found: Boolean,
        val results: List<StampV1>?
    )

    @Serializable
    data class StampV1(
        val interval: IntervalV1,
        @SerialName("skip_type")
        val skipType: String,
        @SerialName("skip_id")
        val skipId: String,
        @SerialName("episode_length")
        val episodeLength: Double
    )

    @Serializable
    data class IntervalV1(
        @SerialName("start_time")
        val startTime: Double,
        @SerialName("end_time")
        val endTime: Double
    )

    @Serializable
    data class Response(
        val found: Boolean,
        val results: List<Stamp>?,
        val message: String?,
        val statusCode: Int
    )

    @Serializable
    data class Stamp(
        val interval: Interval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )

    fun String.getType(context: Context): String {
        val index = when (this) {
            "op" -> 0
            "ed" -> 1
            "mixed-op" -> 2
            "mixed-ed" -> 3
            "recap" -> 4
            else -> -1
        }
        return if (index < 0) this else context.resources.getStringArray(R.array.skipType)[index]
    }

    @Serializable
    data class Interval(
        val startTime: Double,
        val endTime: Double
    )
}