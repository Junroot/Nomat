import { useState } from "react"
import DropDownIcon from "~/assets/drop-down.svg?react"
import DropUpIcon from "~/assets/drop-up.svg?react"

interface DropdownProps {
    values: string[],
    selectedValue: string,
    setValue: (value: string) => void
}

export default function Dropdown({values, selectedValue, setValue}: DropdownProps) {
    const [isOpen, setIsOpen] = useState(false)

    const toggleDropdown = () => setIsOpen(!isOpen)

    function clickValue(value: string) {
        setValue(value)
        setIsOpen(false)
    }

    return (
        <div className="relative">
            <div>
                <button
                    type="button"
                    onClick={toggleDropdown}
                    className="inline-flex flex flex-row w-full rounded-full p-2 h-10 bg-zinc-600 pl-[16px]"
                >
                    <p className="flex-1">{ selectedValue }</p>
                    {isOpen ? <DropUpIcon className="size-6"></DropUpIcon> : <DropDownIcon className="size-6"></DropDownIcon> }
                </button>
            </div>

            {isOpen && (
                <div className="origin-top-right absolute right-0 mt-2 w-full rounded-2xl bg-zinc-600">
                    <div>
                        {
                            values.map((value, index) => 
                                <p key={index} onClick={() => clickValue(value)} className="block rounded-2xl px-4 py-2 hover:bg-zinc-700">
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