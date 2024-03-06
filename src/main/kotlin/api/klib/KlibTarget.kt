/*
 * Copyright 2016-2024 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api.klib


/**
 * Target name consisting of two parts: a [configurableName] that could be configured by a user, and an [targetName]
 * that names a target platform and could not be configured by a user.
 *
 * When serialized, the target represented as a tuple `<name>.<canonicalName>`, like `ios.iosArm64`.
 * If both names are the same (they are by default, unless a user decides to use a custom name), the serialized
 * from is shortened to a single term. For example, `macosArm64.macosArm64` and `macosArm64` are a long and a short
 * serialized forms of the same target.
 */
public class KlibTarget internal constructor(
    public val configurableName: String,
    public val targetName: String)
{
    public companion object {
        public fun parse(line: String): KlibTarget {
            require(line.isNotBlank()) { "Target name could not be blank." }
            if (!line.contains('.')) {
                return KlibTarget(line)
            }
            val parts = line.split('.')
            if (parts.size != 2 || parts.any { it.isBlank() }) {
                throw IllegalArgumentException(
                    "Target has illegal name format: \"$line\", expected: <target name>.<underlying target name>"
                )
            }
            return KlibTarget(parts[1], parts[0])
        }
    }


    override fun toString(): String =
        if (configurableName == targetName) configurableName else "$targetName.$configurableName"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KlibTarget

        if (configurableName != other.configurableName) return false
        if (targetName != other.targetName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = configurableName.hashCode()
        result = 31 * result + targetName.hashCode()
        return result
    }
}

internal fun KlibTarget(name: String) = KlibTarget(name, name)
