package org.kotlogramme.cli.adapter.format

/**
 * How wide a table may be, as the user asked for it with `--table-width`.
 *
 * The terminal's own width is only a default: a person piping a table into a file, or reading it on a
 * screen that is wider or narrower than the terminal reports, can say what they want instead.
 */
sealed interface TableWidth {
    /** The width a table may use, given the width of the terminal [detected] on demand, or `null` for no limit. */
    fun resolve(detected: () -> Int?): Int?

    /** Fit the terminal when the output is one, and leave tables as wide as their content otherwise. */
    data object Detect : TableWidth {
        override fun resolve(detected: () -> Int?): Int? = detected()
    }

    /** Never wrap, whatever the terminal is. */
    data object Unlimited : TableWidth {
        override fun resolve(detected: () -> Int?): Int? = null
    }

    /** Wrap to [columns], terminal or not. */
    data class Fixed(val columns: Int) : TableWidth {
        override fun resolve(detected: () -> Int?): Int? = columns
    }

    companion object {
        /** The setting for the value of `--table-width`: absent detects, `0` is unlimited, anything else is fixed. */
        fun of(option: Int?): TableWidth = when (option) {
            null -> Detect
            0 -> Unlimited
            else -> Fixed(option)
        }
    }
}
