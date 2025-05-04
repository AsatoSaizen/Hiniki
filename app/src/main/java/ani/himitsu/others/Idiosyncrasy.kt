@file:Suppress("UNCHECKED_CAST", "DEPRECATION")

package ani.himitsu.others

import android.content.Intent
import android.os.Bundle
import bit.himitsu.os.Version
import java.io.Serializable

inline fun <reified T : Serializable> Bundle.getSerialized(key: String): T? {
    return if (Version.isTiramisu)
        this.getSerializable(key, T::class.java)
    else
        this.getSerializable(key) as? T
}

inline fun <reified T : Serializable> Intent.getSerialized(key: String): T? {
    return if (Version.isTiramisu)
        this.getSerializableExtra(key, T::class.java)
    else
        this.getSerializableExtra(key) as? T
}