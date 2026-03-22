import { useSearchParams } from "react-router"
import DiscordIcon from "~/assets/discord.svg?react"

export default function LoginView() {
    const [searchParams] = useSearchParams()
    const redirectUrlString = searchParams.get("redirectUrl")
    const redirectUrl = redirectUrlString ? new URL(redirectUrlString) : null

    if (redirectUrl && redirectUrl.origin !== window.location.origin) {
        return (
            <div className="relative w-full h-full flex items-center justify-center overflow-hidden bg-background">
                <NeonBackground />
                <div className="relative z-10 text-center">
                    <h1 className="text-6xl font-black tracking-tight bg-gradient-to-r from-neon-cyan via-neon-purple to-neon-pink bg-clip-text text-transparent mb-4"
                        style={{ textShadow: "0 0 40px rgba(34, 211, 238, 0.3)" }}>
                        NOMAT
                    </h1>
                    <p className="text-xl text-zinc-400">진입 경로가 잘못되었습니다.</p>
                </div>
            </div>
        )
    }

    return (
        <div className="relative w-full h-full flex items-center justify-center overflow-hidden bg-background">
            <NeonBackground />
            <div className="relative z-10 text-center flex flex-col items-center">
                <h1 className="text-7xl font-black tracking-tight bg-gradient-to-r from-neon-cyan via-neon-purple to-neon-pink bg-clip-text text-transparent mb-2"
                    style={{ textShadow: "0 0 40px rgba(34, 211, 238, 0.3)" }}>
                    NOMAT
                </h1>
                <p className="text-zinc-500 text-base mb-10">
                    친구들과 함께하는 노래 맞추기 게임
                </p>
                <button
                    className="inline-flex items-center gap-3 px-8 py-3.5 bg-[#5865f2] rounded-[14px] text-white font-bold text-base cursor-pointer transition-all duration-300 hover:-translate-y-0.5"
                    style={{ boxShadow: "0 0 20px rgba(88, 101, 242, 0.3)" }}
                    onMouseEnter={(e) => { e.currentTarget.style.boxShadow = "0 0 35px rgba(88, 101, 242, 0.5)" }}
                    onMouseLeave={(e) => { e.currentTarget.style.boxShadow = "0 0 20px rgba(88, 101, 242, 0.3)" }}
                    onClick={() => goToDiscordLogin(redirectUrl)}
                >
                    <DiscordIcon className="size-6" />
                    Discord로 시작하기
                </button>
            </div>
        </div>
    )
}

function NeonBackground() {
    return (
        <div className="absolute inset-0 overflow-hidden">
            <div
                className="absolute w-[400px] h-[400px] -top-[100px] -left-[50px]"
                style={{
                    background: "radial-gradient(circle, rgba(34, 211, 238, 0.12), transparent 60%)",
                    animation: "float 6s ease-in-out infinite",
                }}
            />
            <div
                className="absolute w-[300px] h-[300px] -bottom-[80px] -right-[50px]"
                style={{
                    background: "radial-gradient(circle, rgba(167, 139, 250, 0.1), transparent 60%)",
                    animation: "float-reverse 8s ease-in-out infinite",
                }}
            />
        </div>
    )
}

function goToDiscordLogin(redirectUrl: URL | null) {
    const loginPage = window.open(`${import.meta.env.VITE_SERVER_BASE_URL}/oauth2/authorization/discord`)

    if (redirectUrl) {
        const checkLoginPageClosed = setInterval(() => {
            if (!loginPage || loginPage.closed) {
              clearInterval(checkLoginPageClosed);
              window.location.href = redirectUrl.href
            }
          }, 500);
    }
}
