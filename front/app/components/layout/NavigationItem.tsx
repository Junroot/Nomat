import type { ReactNode } from "react";
import React from "react";

interface NavigationItemProperties {
    icon: ReactNode;
    title: String;
    clicked?: Boolean;
    onClick?: () => void;
}

export default function NavigationItem({
    icon,
    title,
    clicked = false,
    onClick = () => {},
}: NavigationItemProperties) {
    return <div
        onClick={onClick}
        className={`relative w-[72px] flex flex-col items-center justify-center gap-1 py-2 rounded-lg transition-all duration-200 ${
            clicked
                ? "bg-[rgba(34,211,238,0.1)] shadow-glow-cyan"
                : "cursor-pointer hover:bg-zinc-800/50"
        }`}
    >
        {clicked && (
            <span className="absolute left-0 top-[10px] w-[2px] h-[24px] rounded-r-sm bg-neon-cyan shadow-[0_0_10px_#22d3ee]" />
        )}
        {React.isValidElement<{ className?: string }>(icon) &&
        React.cloneElement(icon, {
            className: `size-[24px] ${clicked ? "fill-neon-cyan text-neon-cyan drop-shadow-[0_0_8px_rgba(34,211,238,0.5)]" : ""}`,
        })}
        <span className={`text-xs leading-tight ${clicked ? "text-neon-cyan" : "text-zinc-400"}`}>
            {title as string}
        </span>
    </div>
}
