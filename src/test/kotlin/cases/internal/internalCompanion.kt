/*
 * Copyright 2016-2021 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package cases.internal

// Internal companion is not part of public API
// neither should be outer static final companion field
class Companion {
    internal companion object
}

class NamedCompanion {
    internal companion object Foo
}

class NameShadowing {
    internal companion object Companion {
        @JvmStatic
        public val Companion: cases.internal.Companion = Companion()
    }
}
