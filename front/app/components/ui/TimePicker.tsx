import { useRef } from "react";

interface TimePickerProps {
    timeSec: number
    setTimeSec: (value: number) => void
}

export default function TimePicker({timeSec, setTimeSec}: TimePickerProps) {
    const formatedTime = formatTime(timeSec)
    const inputRef = useRef<HTMLInputElement>(null)

    function formatTime(timeSec: number) {
        const second = timeSec % 60
        const minute = Math.floor(timeSec / 60) % 60
        const hour = Math.floor(timeSec / 3600)

        return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:${String(second).padStart(2, "0")}`
    }

    function isValidFormatedTime(formatedTime: string) {
        if (formatedTime.length !== 8) return false

        const hh = parseInt(formatedTime.slice(0, 2))
        const mm = parseInt(formatedTime.slice(3, 5))
        const ss = parseInt(formatedTime.slice(6, 8))

        return hh >= 0 && hh <= 99
            && mm >= 0 && mm <= 59
            && ss >= 0 && ss <= 59
    }

    function convertToTimeSec(timeString: string) {
        const hh = parseInt(timeString.slice(0, 2))
        const mm = parseInt(timeString.slice(3, 5))
        const ss = parseInt(timeString.slice(6, 8))

        return hh * 3600 + mm * 60 + ss
    }

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        const isDigitKey = /^[0-9]$/.test(e.key)
        if (e.key !== "ArrowUp" && e.key !== "ArrowDown" && !isDigitKey) return

        const input = inputRef.current
        if (!input) return

        var pos = input.selectionStart ?? 0

        if (isDigitKey) {
            if (pos === 2 || pos === 5) {
                pos += 1
            }

            const newFormatedTime = formatedTime.slice(0, pos) + e.key + formatedTime.slice(pos + 1)
            if (isValidFormatedTime(newFormatedTime)) {
                setTimeSec(convertToTimeSec(newFormatedTime))
                requestAnimationFrame(() => {
                    input.setSelectionRange(pos + 1, pos + 1)
                })
            }
        } else {
            // 커서 위치에 따라 조절 대상 결정
            if (pos >= 0 && pos <= 2) {
                adjustTime(e.key === "ArrowUp" ? 3600 : -3600)
            } else if (pos >= 3 && pos <= 5) {
                adjustTime(e.key === "ArrowUp" ? 60 : -60);
            } else if (pos >= 6) {
                adjustTime(e.key === "ArrowUp" ? 1 : -1);
            }

            requestAnimationFrame(() => {
                input.setSelectionRange(pos, pos)
            })
        }

        // prevent page scroll
        e.preventDefault();
    }

    const adjustTime = (deltaSec: number): void => {
        const resultTime = timeSec + deltaSec
        if (resultTime < 0) return
        if (resultTime > 99 * 3600 + 59 * 60 + 59) return
        setTimeSec(resultTime)
    }

    return (
        <div className="flex-1 h-10 p-2 shrink bg-zinc-600 text-zinc-200 rounded-full">
            <input
                ref={inputRef}
                type="text"
                value={formatedTime}
                readOnly
                onKeyDown={handleInputKeyDown}
                className="w-full pl-[8px] focus:outline-none"
            />
        </div>
    )
}
