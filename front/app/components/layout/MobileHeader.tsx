import type { ReactNode } from "react";
import React from "react";
import { Link } from "react-router";
import BackArrowIcon from "~/assets/back-arrow.svg?react";
import Me from "~/components/ui/Me";

interface MobileHeaderMainProps {
    variant: "main";
    title: string;
}

interface MobileHeaderSubProps {
    variant: "sub";
    title: string;
    backTo?: string;
    onBack?: () => void;
    actions?: { icon: ReactNode; label: string; onClick: () => void }[];
}

type MobileHeaderProps = MobileHeaderMainProps | MobileHeaderSubProps;

export default function MobileHeader(props: MobileHeaderProps) {
    if (props.variant === "main") {
        return (
            <header className="shrink-0 h-[56px] px-4 flex items-center justify-between bg-surface/80 backdrop-blur-[16px]">
                <h1 className="text-lg font-bold">{props.title}</h1>
                <Me compact />
            </header>
        );
    }

    const { title, backTo, onBack, actions = [] } = props;

    const backButton = (
        <button
            onClick={onBack}
            className="size-[40px] flex items-center justify-center rounded-lg hover:bg-zinc-800/50 transition-colors"
        >
            <BackArrowIcon className="size-[24px]" />
        </button>
    );

    return (
        <header className="shrink-0 h-[56px] px-2 flex items-center bg-surface/80 backdrop-blur-[16px]">
            <div className="shrink-0">
                {backTo ? (
                    <Link to={backTo} replace>
                        {backButton}
                    </Link>
                ) : (
                    backButton
                )}
            </div>
            <h1 className="flex-1 text-center text-lg font-bold truncate px-2">
                {title}
            </h1>
            <div className="shrink-0 flex items-center gap-1">
                {actions.map((action, index) => (
                    <button
                        key={index}
                        onClick={action.onClick}
                        title={action.label}
                        className="size-[40px] flex items-center justify-center rounded-lg hover:bg-zinc-800/50 transition-colors"
                    >
                        {React.isValidElement<{ className?: string }>(action.icon) &&
                            React.cloneElement(action.icon, {
                                className: "size-[24px]",
                            })}
                    </button>
                ))}
            </div>
        </header>
    );
}
