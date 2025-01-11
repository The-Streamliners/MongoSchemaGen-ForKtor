package schema.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FixLenNum(val len: Int)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Regex(val pattern: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Len(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE
)