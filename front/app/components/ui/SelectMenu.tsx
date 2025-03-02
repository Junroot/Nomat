import { useState } from "react";

interface SelectMenuProperties {
    options: Array<string>;
    selectedOption: string;
    selectedHandler: (selectedOption: string) => void;
}

export default function SelectMenu({options, selectedOption, selectedHandler}: SelectMenuProperties) {
    const [isOpen, setIsOpen] = useState(false);
  
    return (
      <div className="relative w-full">
        <button
          onClick={() => setIsOpen(!isOpen)}
          onBlur={() => setTimeout(() => setIsOpen(false), 200)}
          className="w-full py-2 px-4 bg-zinc-600 text-zinc-200 rounded-full flex justify-between items-center focus:outline-none"
        >
          {selectedOption}
          <span className="ml-2">{isOpen ? "▲" : "▼"}</span>
        </button>
  
        {isOpen && (
          <ul className="absolute left-0 mt-2 w-full z-100 bg-zinc-600 text-zinc-200 rounded-lg shadow-lg">
            {options.map((option, index) => (
              <li
                key={index}
                className="p-3 rounded-lg cursor-pointer hover:bg-zinc-400 focus:bg-zinc-100 focus:outline-none"
                onClick={() => {
                  selectedHandler(option);
                  setIsOpen(false);
                }}
              >
                {option}
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }