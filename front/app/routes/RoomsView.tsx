import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react"
import Me from "~/components/ui/Me";
import SearchBar from "~/components/ui/SearchBar";
import { useState } from "react";
import { Link } from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";

export default function RoomsView() {
    const [query, setQuery] = useState("");

    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar >
                <div className="grow shrink flex flex-col gap-4">
                    <NavigationItem clicked icon={<RoomIcon></RoomIcon>} title={"플레이 룸"}></NavigationItem>
                    <Link to="/playlists" replace>
                        <NavigationItem icon={<PlaylistIcon></PlaylistIcon>} title={"플레이리스트"}></NavigationItem>
                    </Link>
                </div>
                <div className="grow-0 shrink-0">
                    <Me nickname="ROOT#3465"></Me>
                </div>
            </NavigationBar>
            <ColumnsContainer>
                <SearchBar query={query} setQuery={setQuery}></SearchBar>
            </ColumnsContainer>
        </div>
      );
}