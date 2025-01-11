package schema.annotation

@Target(AnnotationTarget.CLASS)
annotation class Schema(
    val collName: String
)