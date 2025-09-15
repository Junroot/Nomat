package ilpak.nomat.player.application.domain

enum class RegistrationType(val code: String) {
    DISCORD("DSCD"),
    ;

    companion object {
        private val codeToTypeMap = entries.associateBy { it.code }

        fun fromCode(code: String): RegistrationType? {
            return codeToTypeMap[code]
        }
    }
}
