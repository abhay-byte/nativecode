package com.ivarna.nativecode.core.runtime

sealed interface TerminalLine {
    val text: String

    data class Command(override val text: String) : TerminalLine
    data class Output(override val text: String) : TerminalLine
    data class Error(override val text: String) : TerminalLine
}
