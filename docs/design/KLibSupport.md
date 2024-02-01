The document describes assumptions that led to the current KLib ABI validation implementation.

### Motivation and assumptions

Just like JVM class files, Kotlin/Native libraries (a.k.a. KLibs) comes with a binary compatibility guarantees 
allowing library developers evolve the library without a fear to break projects compiled against previous versions of 
the library (but unlike the JVM, these guarantees are not yet finalized at the time this document was written).

For the JVM, the Binary compatibility validator allows to check if some binary incompatible changes were made and
review what actually changed. For KLibs, there is no such a tool, and it seems reasonable to extend the BCV with KLib
validation support.

There are several assumptions based on the experience of supporting
various multiplatform libraries that drive the KLib validation design:
* Multiplatform libraries usually have both JVM and native targets, so instead of introducing some different 
unrelated tool/plugin it seems reasonable to extend the existing plugin and provide an experience similar to what 
users have now for JVM libraries so that users can verify compatibility for both kinds of targets.
* BCV not only provides a way to verify public ABI changes, but it also allows to check how the public API surface
changed: developers could simply look at the dump file's history in SVC or review the change in a code-review system;
* Projects may have multiple JVM targets, but usually there is only a single target with a single dump file;
At the same time, multiplatform projects have a dozen of different native targets (like `linuxX64`, `iosArm64`, 
`macosArm64`, to name a few), so there should be a way to manage dumps for all the targets.
* Usually, even if a project has multiple native targets, the public ABI exposed by corresponding klibs is either
the same or contains only a small number of differences.
* Not all targets could be compiled on every host: currently, the cross-compilation have some limitations (namely,
it's impossible to build Apple-targets on non macOs-host), and it's unlikely that someday it would be possible to build
Apple-specific sources (i.e., not just common sources that need to be compiled to some iosArm64-klib)
on non Apple-hosts. KLib validation requires a klib, so there should be a way to facilitate klib ABI dumping and 
validation on something different from macOs (consider a multiplatform project where a developer adds a class 
or a function to the common source set, it seems reasonable to expect that it could be done on any host).
* KLibs are emitted not only for native targets, but also for JS and Wasm targets.
* There are scenarios when a klib has to be compiled on a corresponding host platform (i.e., mingw target on Windows
and linux target on Linux), and also there are scenarios when using a Gradle plugin is not an option,
so there should be a way to use BCV for KLibs even in such scenarios. 

### Merged KLib dumps

Assuming that the library's public ABI does not differ significantly between targets, it seems reasonable to merge
all the dumps into a single file where each declaration is annotated with the targets it belongs to. That will minimize
the number of new files to store in a project and will significantly simplify review of the changes. 

The KLib dump is a text format ([see examples in the Kotlin repo](https://github.com/JetBrains/kotlin/blob/master/compiler/util-klib-abi/ReadMe.md)).
For the merged dumps, we can simply extend it by adding special comments with a list of targets, 
like `// Targets: [iosArm64, linuxX64, wasm]`.

Such targets lists could be long and hard to read, so to simplify a process of reviewing changes in a dump we can
replace explicit target names with group aliases corresponding to groups of targets from the
[default hierarchy template](https://kotlinlang.org/docs/multiplatform-hierarchy.html#see-the-full-hierarchy-template).
Then, a long list of all native targets will become `[native]` and, for example, all Android-native targets will
become simply `[androidNative]`.
Of course, the merged dump file should include the mapping between an alias and actual target names, it could be placed
in a file's header.

Here's a brief example of such a merged dump file:
```
// Merged KLib ABI Dump
// Targets: [androidNativeArm32, androidNativeArm64, androidNativeX64, androidNativeX86, linuxArm64, linuxX64, mingwX64]
// Alias: androidNative => [androidNativeArm32, androidNativeArm64, androidNativeX64, androidNativeX86]
// Alias: linux => [linuxArm64, linuxX64]
// Rendering settings:
// - Signature version: 2
// - Show manifest properties: false
// - Show declarations: true

// Library unique name: <testproject>
final class org.different.pack/BuildConfig { // org.different.pack/BuildConfig|null[0]
    constructor <init>() // org.different.pack/BuildConfig.<init>|<init>(){}[0]
    final fun f1(): kotlin/Int // org.different.pack/BuildConfig.f1|f1(){}[0]
    // Targets: [mingwX64]
    final val p1 // org.different.pack/BuildConfig.p1|{}p1[0]
        final fun <get-p1>(): kotlin/Int // org.different.pack/BuildConfig.p1.<get-p1>|<get-p1>(){}[0]
}
// Targets: [linux]
final fun (org.different.pack/BuildConfig).org.different.pack/linuxSpecific(): kotlin/Int // org.different.pack/linuxSpecific|linuxSpecific@org.different.pack.BuildConfig(){}[0]
```

The first line is just a header to check during the parsing. 

The next line (`// Targets: ...`) contains list of all targets for which dumps were merged.
It seems to be excessive to write all the targets each declaration belongs to as the majority
of declarations have the same targets as their parent declarations, and in the case of top level
declarations, until it is mentioned explicitly, the list of targets is the same as the file's target list.

So, the class `BuildConfig` have the same targets as the whole file, but `linuxSpecific`-function
is presented only on `linux`-targets (`// Targets: [linux]`).

The next two lines declare target aliases (`// Alias: <name> => [<targets>]`). There are only two
aliases (`linux` and `androidNative`), because it seems excessive to declare aliases for groups
consisting of only a single target (like `mingw`) as well as declaring aliases for targets
than will never be explicitly presented in a file (like an alias for all the targets, or all native targets). 
We also can't add aliases for targets that does not exist, so there are no aliases like `macos` or `apple` 
(in this example).
However, all other aliases are generated even if it is not explicitly mentioned (like `androidNative`) to
minimize dump's header changes in the future, when such groups could emerge.

After that, a regular KLib ABI dump header continues, with an exception to some declarations being 
annotated with `// Targets`.

So, the dump could be interpreted as follows:
- the dump was merged from individual KLib ABI dumps generated for the following targets:
`androidNativeArm32`, `androidNativeArm64`, `androidNativeX64`, `androidNativeX86`, `linuxArm64`, `linuxX64`, 
`mingwX64`;
- the class `BuildConfig`, its constructor and `f1`-functions are all exposed by klibs for all targets,
but its property `p1` (and the corresponding getter) is declared only for the `mingwX64`-target;
- an extension function `BuildConfig.linuxSpecific` is declared only for `linuxX64` and `linuxArm64` targets.

### Working with dumps on hosts that does not support cross-compilation for all the targets

If a host does not support cross-compilation for all the targets (it's a Linux or Windows host), then
there's no way to both dump and validate klibs for all targets configured for a project.

When it comes to validation, the simplest approach seems to be ignoring unsupported targets (and printing
a warning about it) and validation the ABI only for targets supported by the host compiler.
To do that, corresponding klibs should be built first, and then their ABI should be dumped and merged.
After that, the merged dump stored in the repository should be filtered so that only the declarations for supported
targets are left. Finally, a newly generated dump could be compared with the filtered "golden" dump the same way
dumps are compared for the JVM.

The things are a bit more complicated when it comes to updating the "golden" dump as if only dumps for supported targets
are merged, then the resulting dump file will cause validation failure on the host where all targets
are available (the dump won't contain declaration for Apple-targets, it won't even mention these targets, so when
the ABI validation takes a place on macOs-host, it'll fail).

It seems like there are two ways to handle such a scenario:
- when updating a dump, assume that the ABI for unsupported targets remained the same and update only the ABI 
for supported targets;
- try to guess (or infer) the ABI for unsupported targets by looking at the ABI dumped for supported targets.   

Both approaches have some shortcomings:
- with the first one, as long as the ABI changes, an updated dump will always be incorrect as it won't reflect 
changes for unsupported targets;
- with the second approach, a "guessed" dump may be incorrect (of course, it depends on how we "guess").

By guessing or inferring a dump for unsupported targets, the following procedure is assumed:
- walk up the target hierarchy starting from the unsupported target until a group
consisting of at least one supported target is found;
- assume that declarations shared by all targets in the group found during the previous step are common
to all group members (including unsupported targets);
- generate a dump for unsupported targets by combining this newly generated "common" ABI with all declarations
specific to the unsupported target extracted from an old dump.

The higher the hierarchy we go, the larger the group of targets should be, so the ABI shared by all these targets
should be "more common" (lowering changes of including some target-specific declarations). On the other hand,
if unsupported targets have some target-specific declarations, then it's likely that the targets closer to them in
the hierarchy are also having these declarations.

Here's an example of walking up the target hierarchy for `iosArm64` target on a Linux host for a project
having only Apple-targets and WasmJs-target:
- `iosArm64` is unsupported, let's try any `ios` target;
- all `ios` targets are unavailable, let's try any `apple` target;
- all `apple` targets are unavailable, let's try any `native` target;
- all `native` targets are unavailable, let's try any target;
- `wasmJs` target is available, lets use its dump.

The table below summarizes different scenarios and how the two different approaches handle them.


| Scenario                                                                          | Ignore unsupported target                                                          |            | Guess ABI for unsupported targets                                                                                                                                                                                |            |
|-----------------------------------------------------------------------------------|------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
|                                                                                   | **How it’ll be updated?**                                                          | **Valid?** | **How it’ll be updated?**                                                                                                                                                                                        | **Valid?** |
| **KMP-project without A target-specific ABI**                                     |                                                                                    |            |                                                                                                                                                                                                                  |            |
| Change common declaration                                                         | An Apple-ABI will remain unchanged, but for all other targets the ABI will differ. | No ❌       | The declaration will be declared in all supported targets, we’ll consider it as common and will also add to unsupported (apple) targets without any changes, old apple-ABI will be replaced with the updated one | Yes 👌     |
| Move a declaration from the common sources to every single target-specific source set | An Apple-ABI will remain unchanged, but for all other targets the ABI will differ. | No ❌       | The declaration will be declared in all supported targets, we’ll consider it as common and will also add to unsupported (apple) targets without any changes, old apple-ABI will be replaced with the updated one | Yes 👌     |
| **KMP-project with an Apple-specific ABI**                                        |                                                                                    |            |                                                                                                                                                                                                                  |            | 
| Change a common declaration                                                       | Old version of the declaration will remain in the Apple-ABI dump.                  |No ❌                                                                                                                                              | The declaration will be considered common as all supported targets’ dumps will have it, old apple-specific parts of the ABI will remain untouched|Yes 👌|
| Change an Apple-specific declaration                                              | An Apple-ABI dump won’t get any updates                                            | No ❌                                                                            | An Apple-specific declarations won’t be updated                                                                                                                                                                  | No ❌|
| **KMP-project with a linux-spefic ABI**                                           |                                                                                    |            |                                                                                                                                                                                                                  |            |
| Change a common declaration                                                       | Old version of the declaration will remain in the Apple-ABI dump.                  | No ❌                                                                            | The declaration will be considered common as all supported targets’ dumps will have it, old apple-specific parts of the ABI will remain untouched                                                                | Yes 👌|
| Change a Linux-specific declaration                                               | An Apple-ABI will remain unchanged, but for all other targets the ABI will differ. | Yes 👌                                                                          | An Aplple-ABI will remain untouched                                                                                                                                                                              | Yes 👌|
| **KMP-project with a non-Apple specific ABI (i.e. linux+mingw+js+wasm-specific)** |                                                                                    |            |                                                                                                                                                                                                                  |            |
| Change a declaration for all non-Apple targets                                    | An Apple ABI will remain untouched                                                 | Yes 👌                                                                          | Tha changed ABI will be considered as common (as it came from all the supported targets) and we’ll mark it as an ABI available on Apple targets too.                                                             | No ❌|
| **Module having only Apple-specific ABI**                                         |                                                                                    |            |                                                                                                                                                                                                                  |            |
| Change an ABI                                                                     | No dumps available, the process will fail (or we can just avoid any updates)       | No ❌                                                                            | No dumps available, the process will fail (or we can just avoid any updates)                                                                                                                                     | No ❌|


The "guessing" approach should succeed it a broader spectrum of scenarios, so we decided to use it.

### Programmatic API for KLib ABI validation

To support scenarios when Gradle plugin could not be used (like in the case of Kotlin stdlib),
or when the way users build klibs is different from "compile all the klibs here and now", the programmatic API 
should be provided. The API should simply provide an entry point to all the functionality used by the plugin.

The API is not yet designed and is the subject of further development.
