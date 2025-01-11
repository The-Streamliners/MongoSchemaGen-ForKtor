package schema.ext

import java.io.OutputStream

fun OutputStream.writeText(string: String) {
    write(string.toByteArray())
}