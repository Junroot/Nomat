import { useEffect, useRef, useState } from "react"
import DropDownIcon from "~/assets/drop-down.svg?react"
import DropUpIcon from "~/assets/drop-up.svg?react"

interface DropdownProps {
    values: string[],
    selectedValue: string,
    setValue: (value: string) => void
}

export default function Dropdown({values, selectedValue, setValue}: DropdownProps) {
    const [isOpen, setIsOpen] = useState(false)
    const containerRef = useRef<HTMLDivElement>(null)

    const toggleDropdown = () => setIsOpen(!isOpen)

    useEffect(() => {
        if (!isOpen) return
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setIsOpen(false)
            }
        }
        document.addEventListener("mousedown", handleClickOutside)
        return () => document.removeEventListener("mousedown", handleClickOutside)
    }, [isOpen])

    function clickValue(value: string) {
        setValue(value)
        setIsOpen(false)
    }

    return (
        <div ref={containerRef} className="relative">
            <div>
                <button
                    type="button"
                    onClick={toggleDropdown}
                    className="inline-flex flex flex-row w-full rounded-full p-2 h-10 bg-surface border border-border pl-[16px] transition-colors duration-200 hover:border-neon-cyan"
                >
                    <p className="flex-1">{ selectedValue }</p>
                    {isOpen
                        ? <DropUpIcon className="size-6 text-neon-cyan"></DropUpIcon>
                        : <DropDownIcon className="size-6"></DropDownIcon>
                    }
                </button>
            </div>

            {isOpen && (
                <div className="absolute right-0 mt-2 w-full rounded-2xl bg-card border border-border shadow-glow-cyan origin-top animate-scale-in z-10">
                    <div>
                        {
                            values.map((value, index) =>
                                <p key={index} onClick={() => clickValue(value)} className="block rounded-2xl px-4 py-2 transition-colors duration-200 hover:bg-neon-cyan/10 hover:text-neon-cyan cursor-pointer">
                                    { value }
                                </p>
                            )
                        }
                    </div>
                </div>
            )}
        </div>
  )
}