function Skeleton({ className = "" }: { className?: string }) {
    return <div className={`animate-pulse bg-zinc-700/50 rounded-lg ${className}`} />;
}

export function RoomCardSkeleton() {
    return (
        <div className="bg-gradient-dark border border-border rounded-xl overflow-hidden">
            <Skeleton className="h-[100px] rounded-none" />
            <div className="p-3 space-y-2">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/2" />
            </div>
        </div>
    );
}

export function PlaylistItemSkeleton() {
    return (
        <div className="flex flex-row p-2.5 px-3 gap-3">
            <Skeleton className="size-10 shrink-0" />
            <div className="flex flex-col justify-center gap-1.5 min-w-0 flex-1">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3.5 w-1/2" />
            </div>
        </div>
    );
}

export default Skeleton;
