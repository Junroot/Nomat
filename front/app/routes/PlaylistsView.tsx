import { Link } from "react-router";
import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import Me from "~/components/ui/Me";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react"
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import SelectMenu from "~/components/ui/SelectMenu";
import Column2 from "~/components/layout/Column2";
import { useState } from "react";

export default function PlaylistsView() {
    const searchTypes = ["제목", "제작자"];
    const [selectedSearchType, setSelectedSearchType] = useState(searchTypes[0]);
    const [query, setQuery] = useState("");

    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar >
                <div className="grow shrink flex flex-col gap-4">
                    <Link to="/" replace>
                        <NavigationItem icon={<RoomIcon></RoomIcon>} title={"플레이 룸"}></NavigationItem>
                    </Link>
                    <NavigationItem clicked icon={<PlaylistIcon></PlaylistIcon>} title={"플레이리스트"}></NavigationItem>
                </div>
                <div className="grow-0 shrink-0">
                    <Me nickname="ROOT#3465"></Me>
                </div>
            </NavigationBar>
            <ColumnsContainer>
                <Column1>
                    <div className="flex flex-row p-4 gap-4">
                        <p className="text-4xl font-bold">즐겨찾기</p>
                        <p className="text-4xl font-bold text-zinc-600">내가 만든</p>
                        <p className="text-4xl font-bold text-zinc-600">전체</p>
                    </div>
                    <div className="flex flex-row gap-2">
                        <div className="w-32">
                            <SelectMenu 
                                options={searchTypes} 
                                selectedOption={selectedSearchType} 
                                selectedHandler={(selectedOption: string) => {
                                        setSelectedSearchType(selectedOption);
                                    }                                    
                                } 
                            >
                            </SelectMenu>
                        </div>
                        <div className="w-full h-10 p-2 shrink bg-zinc-600 text-zinc-200 rounded-full">
                            <input
                                type="text"
                                placeholder="검색어"
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                className="w-full pl-[8px] focus:outline-none"
                            />
                        </div>
                    </div>
                    <div className="flex flex-col grow py-2 bg-zinc-800 rounded-xl overflow-y-auto">
                        {
                            [...Array(20)].map((_, i) => 
                                <div key={i} className="flex flex-row p-2 gap-4 hover:bg-zinc-600">
                                    <img 
                                        src="https://img.youtube.com/vi/lWl5viCqGSc/0.jpg"
                                        className="size-16 object-cover"
                                        alt="tumbnail"
                                    >
                                    </img>
                                    <div className="flex flex-col justify-center">
                                        <p className="text-xl font-bold">오늘의 TOP 100: 일본</p>
                                        <p className="text-zinc-400">by. ROOT#3465</p>
                                    </div>
                                </div>
                            )
                        }   
                    </div>
                    <button className="bg-cyan-400 py-2 text-zinc-900 rounded-full cursor-pointer text-2xl font-bold">
                        새 플레이리스트
                    </button>
                </Column1>
                <Column2>
                    <div className="w-full h-full p-4">
                        <div className="w-full h-full bg-zinc-800 text-zinc-200 rounded-lg"></div>
                    </div>
                </Column2>
            </ColumnsContainer>
        </div>
      );
}