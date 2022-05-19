package cases.markerDefault

annotation class HiddenMethod

public class Foo {
    @HiddenMethod
    fun hiddenMethod(bar: Int = 42) {
    }
}

