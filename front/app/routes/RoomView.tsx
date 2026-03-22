import { useParams } from "react-router";
import AppShell from "~/components/layout/AppShell";
import PlayIcon from "~/assets/play.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import UsersIcon from "~/assets/users.svg?react";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";

export default function RoomView() {
    const { roomId } = useParams();
    const title = "들어오셈";
    const playlistTitle = "오늘의 TOP 100: 일본";
    const playlistMater = "ROOT#3465";
    const playlistDescription = "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!"

    return (
        <AppShell
            variant="sub"
            title={title}
            backTo="/"
            actions={[{ icon: <PlayIcon />, label: "시작하기", onClick: () => {} }]}
        >
            <ColumnsContainer>
                <Column1>
                    <p className="text-4xl pt-4 font-bold">{title}</p>
                    <div className="w-full p-4 flex flex-col gap-1 bg-zinc-800 rounded-2xl">
                        <div className="inline-flex items-end gap-1 text-2xl font-bold">
                            <PlaylistIcon className="size-8"></PlaylistIcon>
                            <p>{playlistTitle}</p>
                        </div>
                        <p className="text-md text-zinc-400">by. {playlistMater}</p>
                        <p className="text-md mt-4 text-zinc-200">{playlistDescription}</p>
                    </div>
                    <div className="w-full p-4 flex flex-col gap-2 bg-zinc-800 rounded-2xl">
                        <div className="inline-flex items-end gap-1 text-2xl font-bold">
                            <UsersIcon className="size-8"></UsersIcon>
                            <p>플레이어</p>
                            <p className="text-lg">2/16</p>
                        </div>
                        <div className="flex flex-col p-2 hover:bg-zinc-500 cursor-pointer rounded-md">
                            <div className="flex flex-row items-center gap-2">
                            <UsersIcon className="size-10 rounded-full border border-solid"></UsersIcon>
                                <p>ROOT#3465</p>
                            </div>
                        </div>
                    </div>
                </Column1>
                <Column2>
                    <div className="px-4 pt-4 w-full h-full shrink-1 flex flex-col gap-1 overflow-auto">
                        <div className="flex flex-row gap-2">
                            <UsersIcon className="size-10 rounded-full border border-solid"></UsersIcon>
                            <div className="flex flex-col">
                                <p className="font-bold">Hassium#0436</p>
                                <p>안녕하세요.</p>
                                <p>재밌어보이네요!</p>
                            </div>
                        </div>
                        <div className="flex flex-row gap-2">
                            <UsersIcon className="size-10 rounded-full border border-solid"></UsersIcon>
                            <div className="flex flex-col">
                                <p className="font-bold">Hassium#0436</p>
                                <p>안녕하세요.</p>
                                <p>재밌어보이네요!</p>
                            </div>
                        </div>
                        <div className="flex flex-row gap-2">
                            <UsersIcon className="size-10 rounded-full border border-solid"></UsersIcon>
                            <div className="flex flex-col">
                                <p className="font-bold">Hassium#0436</p>
                                <p>안녕하세요.</p>
                                <p>재밌어보이네요!</p>
                            </div>
                        </div>
                    </div>
                    <div className="p-2 m-2 rounded-full bg-zinc-700">
                        <input
                            type="text"
                            placeholder="보낼 메시지 입력"
                            className="w-full p-[2px] pl-[8px] focus:outline-none"
                        />
                    </div>
                </Column2>
            </ColumnsContainer>
        </AppShell>
    );
};
