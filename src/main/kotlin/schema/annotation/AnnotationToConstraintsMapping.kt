package schema.annotation

inline fun <reified T: Annotation> mapAnnotationToConstraints(annotation: T): Map<String, Any> {
    return when (annotation) {
        is FixLenNum -> {
            buildMap {
                put("pattern", "^\\d{${annotation.len}}$")
            }
        }
        is Regex -> {
            buildMap {
                put("pattern", annotation.pattern)
            }
        }
        is Len -> {
            buildMap {
                put("minLength", annotation.min)
                if (annotation.max != Int.MAX_VALUE) {
                    put("maxLength", annotation.max)
                }
            }
        }
        is NumLimit -> {
            buildMap {
                put("minimum", annotation.min)
                if (annotation.max != Double.MAX_VALUE) {
                    put("maximum", annotation.max)
                }
            }
        }
        else -> error("Can't find annotation to constraint mapping for ${T::class.simpleName}")
    }
}