package schema

object CommonRegex {

    const val REGEX_EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"
    const val REGEX_STRING_MIN_LEN_2 = "^(.*[a-zA-Z]){2,}.*\$"
    const val REGEX_DATE_DD_MM_YYYY = "^(0[1-9]|[12][0-9]|3[01])-(0[1-9]|1[0-2])-\\d{4}\$"

}