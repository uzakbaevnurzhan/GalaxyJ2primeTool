package com.example.ui.analyzer.dat.engine

sealed class DatCommand {
    abstract val originalLine: String

    data class New(val blockSet: DatBlockSet, override val originalLine: String) : DatCommand()
    data class Zero(val blockSet: DatBlockSet, override val originalLine: String) : DatCommand()
    data class Erase(val blockSet: DatBlockSet, override val originalLine: String) : DatCommand()
    data class Move(val content: String, override val originalLine: String) : DatCommand()
    data class Stash(val content: String, override val originalLine: String) : DatCommand()
    data class Free(val content: String, override val originalLine: String) : DatCommand()
    data class Unknown(val line: String, override val originalLine: String) : DatCommand()
}
