/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.validation.api

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*

class ClassVisibility(
    val name: String,
    val flags: Flags?,
    val members: Map<JvmMemberSignature, MemberVisibility>,
    val facadeClassName: String? = null
) {
    val visibility get() = flags
    val isCompanion: Boolean get() = flags != null && Flag.Class.IS_COMPANION_OBJECT(flags)

    var companionVisibilities: ClassVisibility? = null
    val partVisibilities = mutableListOf<ClassVisibility>()
}

fun ClassVisibility.findMember(signature: JvmMemberSignature): MemberVisibility? =
    members[signature] ?: partVisibilities.mapNotNull { it.members[signature] }.firstOrNull()


data class MemberVisibility(val member: JvmMemberSignature, val visibility: Flags?)

private fun isPublic(visibility: Flags?, isPublishedApi: Boolean) =
    visibility == null
            || Flag.IS_PUBLIC(visibility)
            || Flag.IS_PROTECTED(visibility)
            || (isPublishedApi && Flag.IS_INTERNAL(visibility))

fun ClassVisibility.isPublic(isPublishedApi: Boolean) =
    isPublic(visibility, isPublishedApi)

fun MemberVisibility.isPublic(isPublishedApi: Boolean) =
    isPublic(visibility, isPublishedApi)

fun KotlinClassMetadata?.isFileOrMultipartFacade() =
    this is KotlinClassMetadata.FileFacade || this is KotlinClassMetadata.MultiFileClassFacade

fun KotlinClassMetadata?.isSyntheticClass() = this is KotlinClassMetadata.SyntheticClass

private val VISIBILITY_FLAGS_MAP = mapOf(
    Flag.IS_INTERNAL to "internal",
    Flag.IS_PRIVATE to "private",
    Flag.IS_PRIVATE_TO_THIS to "private",
    Flag.IS_PROTECTED to "protected",
    Flag.IS_PUBLIC to "public",
    Flag.IS_LOCAL to "local"
)

private fun Flags.toVisibilityString(): String? =
    VISIBILITY_FLAGS_MAP.entries.firstOrNull { (modifier) -> modifier(this) }?.value

fun KotlinClassMetadata.toClassVisibility(className: String): ClassVisibility? {
    var flags: Flags? = null
    var _facadeClassName: String? = null
    val members = mutableListOf<MemberVisibility>()
    val addMember: (MemberVisibility) -> Unit = { members.add(it) }
    when (this) {
        is KotlinClassMetadata.Class ->
            this.accept(visitClass({ flags = it }, addMember))
        is KotlinClassMetadata.FileFacade ->
            this.accept(visitPackage(addMember))
        is KotlinClassMetadata.MultiFileClassPart -> {
            _facadeClassName = this.facadeClassName
            this.accept(visitPackage(addMember))
        }
        else -> {
        }
    }
    return ClassVisibility(
        className,
        flags,
        members.associateBy { it.member },
        _facadeClassName
    )
}

private fun visitFunction(flags: Flags, name: String, addMember: (MemberVisibility) -> Unit) =
    object : KmFunctionVisitor() {
        var jvmDesc: JvmMemberSignature? = null
        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
            if (type != JvmFunctionExtensionVisitor.TYPE) return null
            return object : JvmFunctionExtensionVisitor() {
                override fun visit(desc: JvmMethodSignature?) {
                    jvmDesc = desc
                }
            }
        }

        override fun visitEnd() {
            jvmDesc?.let { jvmDesc ->
                addMember(MemberVisibility(jvmDesc, flags))
            }
        }
    }

private fun visitConstructor(flags: Flags, addMember: (MemberVisibility) -> Unit) =
    object : KmConstructorVisitor() {
        var jvmDesc: JvmMemberSignature? = null
        override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor? {
            if (type != JvmConstructorExtensionVisitor.TYPE) return null
            return object : JvmConstructorExtensionVisitor() {
                override fun visit(desc: JvmMethodSignature?) {
                    jvmDesc = desc
                }
            }
        }

        override fun visitEnd() {
            jvmDesc?.let { signature ->
                addMember(MemberVisibility(signature, flags))
            }
        }
    }

private fun visitProperty(
    flags: Flags,
    name: String,
    getterFlags: Flags,
    setterFlags: Flags,
    addMember: (MemberVisibility) -> Unit
) =
    object : KmPropertyVisitor() {
        var _fieldDesc: JvmMemberSignature? = null
        var _getterDesc: JvmMemberSignature? = null
        var _setterDesc: JvmMemberSignature? = null

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            if (type != JvmPropertyExtensionVisitor.TYPE) return null
            return object : JvmPropertyExtensionVisitor() {
                override fun visit(
                    jvmFlags: Flags,
                    fieldSignature: JvmFieldSignature?,
                    getterSignature: JvmMethodSignature?,
                    setterSignature: JvmMethodSignature?
                ) {
                    _fieldDesc = fieldSignature
                    _getterDesc = getterSignature
                    _setterDesc = setterSignature
                }
            }
        }

        override fun visitEnd() {
            _getterDesc?.let { addMember(MemberVisibility(it, getterFlags)) }
            _setterDesc?.let { addMember(MemberVisibility(it, setterFlags)) }
            _fieldDesc?.let {
                val fieldVisibility = when {
                    Flag.Property.IS_LATEINIT(flags) -> setterFlags
                    _getterDesc == null && _setterDesc == null -> flags // JvmField or const case
                    else -> flagsOf(Flag.IS_PRIVATE)
                }
                addMember(MemberVisibility(it, fieldVisibility))
            }
        }
    }

private fun visitPackage(addMember: (MemberVisibility) -> Unit) =
    object : KmPackageVisitor() {
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
            return visitFunction(flags, name, addMember)
        }

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor? {
            return visitProperty(
                flags,
                name,
                getterFlags,
                setterFlags,
                addMember
            )
        }

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? {
            return super.visitTypeAlias(flags, name)
        }
    }

private fun visitClass(flags: (Flags) -> Unit, addMember: (MemberVisibility) -> Unit) =
    object : KmClassVisitor() {
        override fun visit(flags: Flags, name: ClassName) {
            flags(flags)
        }

        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
            return visitFunction(flags, name, addMember)
        }

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor? {
            return visitProperty(
                flags,
                name,
                getterFlags,
                setterFlags,
                addMember
            )
        }

        override fun visitConstructor(flags: Flags): KmConstructorVisitor? {
            return visitConstructor(flags, addMember)
        }

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? {
            return super.visitTypeAlias(flags, name)
        }
    }

