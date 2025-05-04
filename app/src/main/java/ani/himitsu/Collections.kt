package ani.himitsu

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KFunction

fun <A, B> Collection<A>.asyncMap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async { f(it) } }.map { it.await() }
}

fun <A, B> Collection<A>.asyncMapNotNull(f: suspend (A) -> B?): List<B> = runBlocking {
    map { async { f(it) } }.mapNotNull { it.await() }
}

//Credits to leg
data class Lazier<T>(
    val factory: () -> T,
    val name: String,
    val lClass: KFunction<T>? = null
) {
    val get = lazy { factory() ?: lClass?.call() }
}

fun <T> lazyList(vararg objects: Pair<String, () -> T>): List<Lazier<T>> {
    return objects.map {
        Lazier(it.second, it.first)
    }
}