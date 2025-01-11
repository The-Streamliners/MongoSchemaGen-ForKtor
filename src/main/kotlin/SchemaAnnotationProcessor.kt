import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import com.google.gson.Gson
import schema.CommonRegex.REGEX_EMAIL
import schema.CommonRegex.REGEX_STRING_MIN_LEN_2
import schema.annotation.*
import schema.ext.*
import kotlin.reflect.KClass

@OptIn(KspExperimental::class)
class SchemaAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val gson = Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val fieldToConstraintsMap = mutableMapOf<String, Map<String, Any>>()
    private val defaultConstraintExceptions = mutableListOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(Schema::class.qualifiedName!!)

        processFieldAnnotation(FixLenNum::class, resolver)
        processFieldAnnotation(Regex::class, resolver)
        processFieldAnnotation(Len::class, resolver)
        processFieldAnnotation(NumLimit::class, resolver)
        processDefaultConstraintExceptionAnnotation(resolver)

        val invalidAnnotatedClasses = annotatedClasses.filter { !it.validate() }.toList()

        val schemas = annotatedClasses
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .associate { classSymbol ->
                val className = classSymbol.simpleName.asString()
                val schema = mapOf(
                    "@%@jsonSchema" to buildSchema(classSymbol, resolver)
                )

                val collName = classSymbol.getAnnotationsByType(Schema::class).firstOrNull()?.collName
                    ?: error("Unable to get collName")

                writeSchemasToJsonFile("${className}Schema", schema)

                collName to schema
            }

        writeSchemasToKotlinFile(schemas)

        return invalidAnnotatedClasses
    }

    private fun writeSchemasToJsonFile(fileName: String, schema: Map<String, Any>) {
        val file = codeGenerator.createNewFile(Dependencies(true), "schema" , fileName, "json")
        file.writeText(gson.toJson(schema).replace("@%@", "$"))
        file.close()
    }

    private fun writeSchemasToKotlinFile(schemas: Map<String, Map<String, Any>>) {
        if (schemas.isEmpty()) return

        val packageName = "schema"
        val fileName = "GeneratedSchemas"
        val file = codeGenerator.createNewFile(Dependencies(true),  packageName, fileName)
        file.writeText("package $packageName\n\n")
        file.writeText("object $fileName {\n")

        file.writeText("\n\tfun get(): Map<String, String> = buildMap {\n")

        schemas.forEach { (name, schema) ->
            val json = gson.toJson(schema)
            file.writeText("\n\t\tput(\"$name\", \"\"\"\n$json\n\"\"\".trimIndent().replace(\"@%@\", \"\$\"))\n")
        }

        file.writeText("\n\t}\n}")
    }

    private inline fun <reified T : Annotation> processFieldAnnotation(
        annotationType: KClass<T>, resolver: Resolver
    ) {
        resolver.getSymbolsWithAnnotation(annotationType.qualifiedName!!)
            .forEach { prop ->
                val field = getKeyForFieldToConstraintsMap((prop.parent?.parent as? KSClassDeclaration), (prop as KSValueParameter))
                val annotation = prop.getAnnotationsByType(annotationType).firstOrNull()
                    ?: error("Annotation ${annotationType.simpleName} not found on $field")

                fieldToConstraintsMap[field] = mapAnnotationToConstraints(annotation)
            }
    }

    private fun processDefaultConstraintExceptionAnnotation(
        resolver: Resolver
    ) {
        resolver.getSymbolsWithAnnotation(NoDef::class.qualifiedName!!)
            .forEach { prop ->
                val field = getKeyForFieldToConstraintsMap((prop.parent?.parent as? KSClassDeclaration), (prop as KSValueParameter))
                defaultConstraintExceptions.add(field)
            }
    }

    private fun getKeyForFieldToConstraintsMap(
        classSymbol: KSClassDeclaration?,
        valueParam: KSValueParameter? = null,
        field: String? = null
    ): String {
        val className = classSymbol?.simpleName?.asString() ?: error("Unable to get className")
        val fieldName = field ?: valueParam?.name?.asString() ?: error("Unable to get fieldName")
        return "$className#$fieldName"
    }

    private fun buildSchema(classSymbol: KSClassDeclaration, resolver: Resolver): Map<String, Any> {
        val schema = mutableMapOf<String, Any>()

        val properties = classSymbol.declarations.filterIsInstance<KSPropertyDeclaration>()
        val requiredProperties = mutableListOf<String>()
        val fieldSchemas = mutableMapOf<String, Any>()

        properties.forEach { propertySymbol ->

            val fieldName = propertySymbol.simpleName.asString()

            val fieldSchema = mutableMapOf<String, Any>()

            if (!propertySymbol.isNullable()) {
                requiredProperties.add(fieldName)
            }

            val type = propertySymbol.type.mongoType()

            if (type == "object") {
                val embeddedClassSymbol = propertySymbol.type.resolve().declaration.closestClassDeclaration()
                    ?: error("Unable to get ClassDeclaration for ${propertySymbol.type}")
                val embeddedClassSchema = buildSchema(embeddedClassSymbol, resolver)
                fieldSchemas[fieldName] = embeddedClassSchema
                return@forEach
            }

            fieldSchema["bsonType"] = type.run {
                if (!requiredProperties.contains(fieldName)) listOf(this, "null") else this
            }

            val field = getKeyForFieldToConstraintsMap(classSymbol, field = fieldName)
            val constraints = fieldToConstraintsMap.getOrDefault(field, emptyMap()).toMutableMap()

            if (propertySymbol.type.isEnum()) {
                constraints["enum"] = propertySymbol.type.enumValues().run {
                    if (!requiredProperties.contains(fieldName)) this + null else this
                }
            }

            constraints.forEach { (key, value) ->
                fieldSchema[key] =  value
            }

            if (constraints.isEmpty() && !defaultConstraintExceptions.contains(field)) {
                addDefaultConstraintsToField(fieldName, type, fieldSchema)
            }

            if (type == "array") {
                val arrayType = propertySymbol.type.resolve().arguments.firstOrNull()?.type
                    ?: error("List type not found")
                val mongoType = arrayType.mongoType()

                fieldSchema["items"] = if (mongoType == "object") {
                    val objectType = arrayType.resolve().declaration.closestClassDeclaration()
                        ?: error("List type not found")
                    buildSchema(objectType, resolver)
                } else {
                    mapOf(
                        "bsonType" to mongoType
                    )
                }
            }

            fieldSchemas[fieldName] = fieldSchema
        }

        schema["bsonType"] = "object"
        if (requiredProperties.isNotEmpty()) {
            schema["required"] = requiredProperties
        }
        schema["properties"] = fieldSchemas

        return schema
    }

    private fun addDefaultConstraintsToField(
        field: String,
        type: String,
        fieldSchema: MutableMap<String, Any>
    ) {
        if (field.contains("email") && type == "string") {
            fieldSchema["pattern"] = REGEX_EMAIL
            return
        }

        when (type) {
            "string" -> {
                fieldSchema["pattern"] = REGEX_STRING_MIN_LEN_2
            }
            "number" -> {
                fieldSchema["minimum"] = 1
            }
        }
    }
}

class SchemaAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return SchemaAnnotationProcessor(environment.codeGenerator, environment.logger)
    }
}
