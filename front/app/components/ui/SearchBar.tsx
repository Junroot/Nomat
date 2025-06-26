import SearchIcon from "~/assets/search.svg?react"

interface SearchBarProps {
  query: string;
  setQuery: (query: string) => void;
}

export default function SearchBar({ query, setQuery }: SearchBarProps) {
    return (
      <div className="w-full p-[8px]">
        <div className="w-full h-[48px] p-[8px] flex flex-row rounded-full bg-zinc-700">
            <SearchIcon className="size-[24px] m-[4px]"></SearchIcon>
            <input
                type="text"
                placeholder="방 이름, 플레이리스트, 플레이어 이름으로 검색"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full p-[2px] pl-[8px] focus:outline-none"
            />
        </div>   
      </div>
    )
}