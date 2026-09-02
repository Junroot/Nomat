import type { ReactNode } from "react";
import { Link, useLocation } from "react-router";
import { motion } from "framer-motion";
import useBreakpoint from "~/hooks/useBreakpoint";
import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import MobileHeader from "~/components/layout/MobileHeader";
import MobileBottomNav from "~/components/layout/MobileBottomNav";
import Me from "~/components/ui/Me";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import BackIcon from "~/assets/back.svg?react";

function AnimatedContent({ className, children }: { className?: string; children: ReactNode }) {
    const { pathname } = useLocation();
    return (
        <motion.div
            key={pathname}
            className={className}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.15 }}
        >
            {children}
        </motion.div>
    );
}

interface AppShellMainProps {
    variant: "main";
    activeTab: "rooms" | "playlists";
    title: string;
    children: ReactNode;
}

interface AppShellSubProps {
    variant: "sub";
    title: string;
    backTo?: string;
    onBack?: () => void;
    actions?: { icon: ReactNode; label: string; onClick: () => void }[];
    children: ReactNode;
}

type AppShellProps = AppShellMainProps | AppShellSubProps;

export default function AppShell(props: AppShellProps) {
    const { isMobile } = useBreakpoint();

    if (props.variant === "main") {
        return <MainShell {...props} isMobile={isMobile} />;
    }

    return <SubShell {...props} isMobile={isMobile} />;
}

function MainShell({
    activeTab,
    title,
    children,
    isMobile,
}: AppShellMainProps & { isMobile: boolean }) {
    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar>
                <div className="grow shrink flex flex-col gap-4">
                    <Link to="/" replace>
                        <NavigationItem
                            clicked={activeTab === "rooms"}
                            icon={<RoomIcon />}
                            title="플레이 룸"
                        />
                    </Link>
                    <Link to="/playlists" replace>
                        <NavigationItem
                            clicked={activeTab === "playlists"}
                            icon={<PlaylistIcon />}
                            title="플레이리스트"
                        />
                    </Link>
                </div>
                <div className="grow-0 shrink-0">
                    <Me />
                </div>
            </NavigationBar>

            {/* ⚠️ `isMobile`로 **엘리먼트 트리를 갈아끼우지 않는다.** 같은 자리에 다른 타입이 오면
                React가 그 아래를 통째로 언마운트·재마운트하고, `children` 안의 iframe(라운드 오디오
                플레이어)이 파괴·재생성돼 재생이 끊긴다. 골격은 고정하고 모바일 전용 요소의 유무와
                className만 바꾼다 — `AnimatedContent`의 자리가 유지되므로 subtree가 살아남는다. */}
            <div className="flex-1 flex flex-col h-full min-h-0">
                {isMobile && <MobileHeader variant="main" title={title} />}
                <AnimatedContent className={isMobile ? "flex-1 overflow-auto" : "flex-1 h-full"}>
                    {children}
                </AnimatedContent>
                {isMobile && <MobileBottomNav activeTab={activeTab} />}
            </div>
        </div>
    );
}

function SubShell({
    title,
    backTo,
    onBack,
    actions = [],
    children,
    isMobile,
}: AppShellSubProps & { isMobile: boolean }) {
    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar>
                <div className="grow shrink flex flex-col gap-4">
                    {backTo ? (
                        <Link to={backTo} replace>
                            <NavigationItem icon={<BackIcon />} title="뒤로가기" />
                        </Link>
                    ) : onBack ? (
                        <NavigationItem
                            icon={<BackIcon />}
                            title="뒤로가기"
                            onClick={onBack}
                        />
                    ) : null}
                    {actions.map((action, index) => (
                        <NavigationItem
                            key={index}
                            icon={action.icon}
                            title={action.label}
                            onClick={action.onClick}
                        />
                    ))}
                </div>
                <div className="grow-0 shrink-0">
                    <Me />
                </div>
            </NavigationBar>

            {/* 골격을 고정하는 이유는 MainShell의 주석 참조 — 방 화면의 플레이어가 여기에 달려 있다. */}
            <div className="flex-1 flex flex-col h-full min-h-0">
                {isMobile && (
                    <MobileHeader
                        variant="sub"
                        title={title}
                        backTo={backTo}
                        onBack={onBack}
                        actions={actions}
                    />
                )}
                <AnimatedContent className={isMobile ? "flex-1 overflow-auto" : "flex-1 h-full"}>
                    {children}
                </AnimatedContent>
            </div>
        </div>
    );
}
