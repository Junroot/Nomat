import { useSearchParams } from "react-router"
import DiscordIcon from "~/assets/discord.svg?react"
import MeStore from "~/stores/MeStore"

export default function LoginView() {
    const [searchParams] = useSearchParams()
    const redirectUrlString = searchParams.get("redirectUrl")
    const redirectUrl = redirectUrlString ? new URL(redirectUrlString) : null
    const meStore = MeStore()

    if (redirectUrl && redirectUrl.origin !== window.location.origin) {
        return <div className="w-full h-full flex flex-col justify-center items-center gap-24">
            <p className="text-5xl font-bold">로그인</p>
            <p className="text-3xl">진입 경로가 잘못되었습니다.</p>
    </div>
    }

    return <div className="w-full h-full flex flex-col justify-center items-center gap-24">
        <p className="text-5xl font-bold">로그인</p>
        <div className="flex flex-row">
            <button
                className="size-24 rounded-full bg-[#5865f2] cursor-pointer"
                onClick={() => goToDiscordLogin(redirectUrl)}
            >
                <DiscordIcon className="size-16 mx-4 my-4"></DiscordIcon>
            </button>
        </div>
    </div>
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
