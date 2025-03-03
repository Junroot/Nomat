import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
    index("routes/RoomsView.tsx"),
    route("rooms/:roomId", "routes/RoomView.tsx"),
    route("playlists", "routes/PlaylistsView.tsx"),
    route("login", "routes/LoginView.tsx"),
] satisfies RouteConfig;
