package ilpak.nomat.room.application.domain

enum class RoomEntryResult(val code: Int) {
    SUCCESS(0),
    ALREADY_JOINED(1),
    ROOM_FULL(2),
    ;

    companion object {
        private val codeToResultMap = entries.associateBy { it.code }

        fun fromCode(code: Int?): RoomEntryResult? = code?.let { codeToResultMap[code] }
    }
}
