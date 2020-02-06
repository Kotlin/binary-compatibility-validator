/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.validation.api

import kotlinx.metadata.jvm.*
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.io.*

fun Sequence<InputStream>.loadApiFromJvmClasses(visibilityFilter: (String) -> Boolean = { true }): Sequence<ClassBinarySignature> {
    val classNodes = map {
        it.use { stream ->
            val classNode = ClassNode()
            ClassReader(stream).accept(classNode, ClassReader.SKIP_CODE)
            classNode
        }
    }

    val visibilityMapNew = classNodes.readKotlinVisibilities().filterKeys(visibilityFilter)

    return classNodes
        .map { classNode ->
            with(classNode) {
                val metadata = loadKotlinMetadata()
                val mVisibility = visibilityMapNew[name]
                val classAccess =
                    AccessFlags(effectiveAccess and Opcodes.ACC_STATIC.inv())

                val supertypes = listOf(superName) - "java/lang/Object" + interfaces.sorted()

                val memberSignatures = (
                        fields.map {
                            with(it) {
                                FieldBinarySignature(
                                    JvmFieldSignature(name, desc),
                                    isPublishedApi(),
                                    AccessFlags(access)
                                )
                            }
                        } +
                                methods.map {
                                    with(it) {
                                        MethodBinarySignature(
                                            JvmMethodSignature(name, desc),
                                            isPublishedApi(),
                                            AccessFlags(access)
                                        )
                                    }
                                }
                        ).filter {
                    it.isEffectivelyPublic(classAccess, mVisibility)
                }

                ClassBinarySignature(
                    name, superName, outerClassName, supertypes, memberSignatures, classAccess,
                    isEffectivelyPublic(mVisibility), metadata.isFileOrMultipartFacade() || isDefaultImpls(
                        metadata
                    )
                )
            }
        }
}

fun Sequence<ClassBinarySignature>.filterOutNonPublic(nonPublicPackages: Set<String> = emptySet()): Sequence<ClassBinarySignature> {
    val nonPublicPaths = nonPublicPackages.map { it.replace('.', '/') + '/' }
    val classByName = associateBy { it.name }

    fun ClassBinarySignature.isInNonPublicPackage() =
        nonPublicPaths.any { name.startsWith(it) }

    fun ClassBinarySignature.isPublicAndAccessible(): Boolean =
        isEffectivelyPublic &&
                (outerName == null || classByName[outerName]?.let { outerClass ->
                    !(this.access.isProtected && outerClass.access.isFinal)
                            && outerClass.isPublicAndAccessible()
                } ?: true)

    fun supertypes(superName: String) = generateSequence({ classByName[superName] }, { classByName[it.superName] })

    fun ClassBinarySignature.flattenNonPublicBases(): ClassBinarySignature {

        val nonPublicSupertypes = supertypes(superName).takeWhile { !it.isPublicAndAccessible() }.toList()
        if (nonPublicSupertypes.isEmpty())
            return this

        val inheritedStaticSignatures =
            nonPublicSupertypes.flatMap { it.memberSignatures.filter { it.access.isStatic } }

        // not covered the case when there is public superclass after chain of private superclasses
        return this.copy(
            memberSignatures = memberSignatures + inheritedStaticSignatures,
            supertypes = supertypes - superName
        )
    }

    return filter { !it.isInNonPublicPackage() && it.isPublicAndAccessible() }
        .map { it.flattenNonPublicBases() }
        .filterNot { it.isNotUsedWhenEmpty && it.memberSignatures.isEmpty() }
}
