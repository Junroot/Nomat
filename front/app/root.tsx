import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import type { Route } from "./+types/root";
import Toaster from "~/components/ui/Toast";
import "./app.css";

export const links: Route.LinksFunction = () => [
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&display=swap",
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <Toaster />
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let code = "";
  let message = "문제가 발생했습니다";
  let details = "알 수 없는 오류가 발생했습니다.";
  let stack: string | undefined;
  let isNotFound = false;
  let isRouteError = false;

  if (isRouteErrorResponse(error)) {
    isRouteError = true;
    code = String(error.status);
    if (error.status === 404) {
      isNotFound = true;
      message = "페이지를 찾을 수 없습니다";
      details = "요청하신 페이지가 존재하지 않거나 이동되었습니다.";
    } else {
      message = "오류가 발생했습니다";
      details = error.statusText || `서버에서 ${error.status} 오류가 반환되었습니다.`;
    }
  } else if (error instanceof Error) {
    if (import.meta.env.DEV) {
      details = error.message;
      stack = error.stack;
    }
  }

  return (
    <main className="relative w-full h-full flex items-center justify-center overflow-hidden bg-background">
      <div className="absolute inset-0 overflow-hidden">
        <div
          className="absolute w-[400px] h-[400px] -top-[100px] -left-[50px]"
          style={{
            background:
              "radial-gradient(circle, rgba(34, 211, 238, 0.12), transparent 60%)",
            animation: "float 6s ease-in-out infinite",
          }}
        />
        <div
          className="absolute w-[300px] h-[300px] -bottom-[80px] -right-[50px]"
          style={{
            background:
              "radial-gradient(circle, rgba(167, 139, 250, 0.1), transparent 60%)",
            animation: "float-reverse 8s ease-in-out infinite",
          }}
        />
      </div>

      <div className="relative z-10 text-center flex flex-col items-center px-4">
        {code && (
          <p
            className="text-8xl font-black tracking-tight bg-gradient-to-r from-neon-cyan via-neon-purple to-neon-pink bg-clip-text text-transparent mb-4"
            style={{ textShadow: "0 0 40px rgba(34, 211, 238, 0.3)" }}
          >
            {code}
          </p>
        )}
        <h1 className="text-2xl font-bold text-zinc-100 mb-2">{message}</h1>
        <p className="text-zinc-400 mb-8 max-w-md">{details}</p>

        <div className="flex gap-3">
          {isNotFound ? (
            <a
              href="/"
              className="inline-flex items-center px-6 py-2.5 rounded-xl bg-neon-cyan/10 text-neon-cyan font-semibold text-sm border border-neon-cyan/20 transition-all duration-300 hover:-translate-y-0.5 hover:bg-neon-cyan/15"
              style={{ boxShadow: "0 0 15px rgba(34, 211, 238, 0.15)" }}
            >
              홈으로 돌아가기
            </a>
          ) : (
            <button
              onClick={() => window.location.reload()}
              className="inline-flex items-center px-6 py-2.5 rounded-xl bg-neon-cyan/10 text-neon-cyan font-semibold text-sm border border-neon-cyan/20 cursor-pointer transition-all duration-300 hover:-translate-y-0.5 hover:bg-neon-cyan/15"
              style={{ boxShadow: "0 0 15px rgba(34, 211, 238, 0.15)" }}
            >
              다시 시도
            </button>
          )}
          {isRouteError && !isNotFound && (
            <a
              href="/"
              className="inline-flex items-center px-6 py-2.5 rounded-xl bg-zinc-800 text-zinc-300 font-semibold text-sm border border-zinc-700 transition-all duration-300 hover:-translate-y-0.5 hover:bg-zinc-700"
            >
              홈으로 돌아가기
            </a>
          )}
        </div>

        {stack && (
          <pre className="mt-8 w-full max-w-2xl p-4 rounded-xl bg-zinc-900/80 border border-zinc-800 text-left text-xs text-zinc-400 overflow-x-auto">
            <code>{stack}</code>
          </pre>
        )}
      </div>
    </main>
  );
}
