package schema.ext

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import java.util.Date

internal fun KSPropertyDeclaration.isNullable(): Boolean {
    if (getter == null) {
        return false
    }

    return getter?.returnType?.resolve()?.isMarkedNullable ?: false
}

internal fun KSTypeReference.mongoType(): String {
    if (isEnum()) return "string"

    return when (val type = toString()) {
        String::class.simpleName,
        Date::class.simpleName -> type.lowercase()
        Int::class.simpleName,
        Long::class.simpleName,
        Double::class.simpleName,
        Float::class.simpleName -> "number"
        List::class.simpleName -> "array"
        Boolean::class.simpleName -> "bool"
        "ObjectId" -> "objectId"
        else -> "object"
    }
}

internal fun KSTypeReference.isEnum(): Boolean {
    val propertyType = resolve().declaration

    return propertyType is KSClassDeclaration
            && propertyType.classKind == ClassKind.ENUM_CLASS
}

internal fun KSTypeReference.enumValues(): List<String> {
    val propertyType = resolve().declaration

    if (propertyType is KSClassDeclaration && propertyType.classKind == ClassKind.ENUM_CLASS) {
        val values = propertyType.declarations
            .map { it.simpleName.asString() }
            .toMutableList()

        values.remove("<init>")

        return values
    }

    error("Field is not enum")
}