<script lang="ts">
  import type { NavigationItemData } from "../../../components/navigation-bar/navigation-item.ts";

  import ChatInput from "../../../components/room/chats/chat-input.svelte";
  import Chats from "../../../components/room/chats/chats.svelte";
  import NavigationBar from "../../../components/navigation-bar/navigation-bar.svelte";
  import Players from "../../../components/room//players/players.svelte";
  import ArrowBackIcon from "../../../icons/arrow-back-icon.svelte";
  import PlayIcon from "../../../icons/play-icon.svelte";
  import RoomPlaylist from "../../../components/room/room_playlist.svelte";

  let roomName = "들어오셈";
  let navigationItems: NavigationItemData[] = [
    {
      iconComponent: ArrowBackIcon,
      label: "대기실",
      onClick: () => {
        window.location.href = "/";
      },
    },
    {
      iconComponent: PlayIcon,
      label: "시작",
    },
  ];
</script>

<div class="w-screen h-screen overflow-hidden flex flex-row layout-container">
  <NavigationBar {navigationItems}></NavigationBar>
  <div class="p-2 flex-auto flex flex-row gap-2 room">
    <div class="basis-1/3 flex flex-col overflow-auto hide-scrollbar gap-2">
      <div class="h1 font-bold p-2">{roomName}</div>
      <div><RoomPlaylist></RoomPlaylist></div>
      <div class="h-full"><Players></Players></div>
    </div>
    <div class="basis-2/3 flex flex-col-reverse gap-2">
      <div class="w-full h-16"><ChatInput></ChatInput></div>
      <div class="w-full h-full overflow-auto"><Chats></Chats></div>
    </div>
  </div>
</div>

<style>
  .room {
    background-color: var(--background-color);
    color: var(--font-light);
  }
</style>
