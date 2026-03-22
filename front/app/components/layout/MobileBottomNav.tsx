import { useState } from "react";
import { Link } from "react-router";
import React from "react";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import UserIcon from "~/assets/user.svg?react";
import MeStore from "~/stores/MeStore";
import { getRegistrationCode } from "~/utils/registrationCode";

interface MobileBottomNavProps {
    activeTab: "rooms" | "playlists";
}

export default function MobileBottomNav({ activeTab }: MobileBottomNavProps) {
    const [showProfile, setShowProfile] = useState(false);
    const meStore = MeStore();
    const me = meStore.me;

    const tabs = [
        { key: "rooms" as const, to: "/", icon: <RoomIcon />, label: "플레이 룸" },
        { key: "playlists" as const, to: "/playlists", icon: <PlaylistIcon />, label: "플레이리스트" },
    ];

    return (
        <nav className="shrink-0 h-[64px] relative bg-surface/80 backdrop-blur-[16px]">
            {showProfile && me && (
                <div className="absolute bottom-[68px] right-2 w-48 p-3 rounded-xl bg-zinc-800 border border-border shadow-lg">
                    <div className="flex items-center gap-2">
                        <div className="size-[32px] flex items-center justify-center rounded-full bg-zinc-700">
                            <UserIcon className="size-[20px]" />
                        </div>
                        <div className="flex flex-col min-w-0">
                            <p className="text-sm font-bold truncate">{me.nickname}</p>
                            <p className="text-xs text-zinc-400">#{getRegistrationCode(me.registrationType)}</p>
                        </div>
                    </div>
                </div>
            )}
            <div className="h-full flex items-center justify-around px-4">
                {tabs.map((tab) => {
                    const isActive = activeTab === tab.key;
                    return (
                        <Link
                            key={tab.key}
                            to={tab.to}
                            replace
                            className={`flex flex-col items-center justify-center gap-1 px-4 py-2 rounded-xl transition-all duration-200 ${
                                isActive
                                    ? "bg-[rgba(34,211,238,0.1)]"
                                    : ""
                            }`}
                        >
                            {React.isValidElement<{ className?: string }>(tab.icon) &&
                                React.cloneElement(tab.icon, {
                                    className: `size-[24px] ${
                                        isActive
                                            ? "fill-neon-cyan text-neon-cyan drop-shadow-[0_0_8px_rgba(34,211,238,0.5)]"
                                            : ""
                                    }`,
                                })}
                            <span className={`text-xs ${isActive ? "text-neon-cyan" : "text-zinc-400"}`}>
                                {tab.label}
                            </span>
                        </Link>
                    );
                })}
                <button
                    onClick={() => setShowProfile(!showProfile)}
                    className={`flex flex-col items-center justify-center gap-1 px-4 py-2 rounded-xl transition-all duration-200 ${
                        showProfile ? "bg-[rgba(34,211,238,0.1)]" : ""
                    }`}
                >
                    <UserIcon
                        className={`size-[24px] ${
                            showProfile
                                ? "fill-neon-cyan text-neon-cyan drop-shadow-[0_0_8px_rgba(34,211,238,0.5)]"
                                : ""
                        }`}
                    />
                    <span className={`text-xs ${showProfile ? "text-neon-cyan" : "text-zinc-400"}`}>
                        프로필
                    </span>
                </button>
            </div>
        </nav>
    );
}
