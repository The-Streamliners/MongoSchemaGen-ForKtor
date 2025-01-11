package schema.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NumLimit(
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE
)