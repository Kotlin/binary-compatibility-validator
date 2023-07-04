package cases.enums

@OptIn(ExperimentalStdlibApi::class)
fun test() {
    EnumClass.entries.forEach {
        println(it)
    }
}
