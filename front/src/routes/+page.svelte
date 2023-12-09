<script lang="ts">
  import type { NavigationItemData } from "../components/navigation-bar/navigation-item";

  import Room from "../components/lobby/room.svelte";
  import CreateRoomIcon from "../icons/create-room-icon.svelte";
  import NavigationBar from "../components/navigation-bar/navigation-bar.svelte";
  import SearchInput from "../components/lobby/search-input.svelte";
  import Me from "../components/lobby/me.svelte";
  import RoomCreate from "../components/lobby/room-create.svelte";

  let isCreatingRoom: boolean = false;
  let navigationItems: NavigationItemData[] = [
    {
      iconComponent: CreateRoomIcon,
      label: "방 만들기",
      onClick: () => {
        isCreatingRoom = true;
      },
    },
  ];

  let rooms = [
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
    {
      roomName: "들어오셈",
      playListName: "오늘의 TOP 100: 일본",
      playListCount: 100,
      roomMaster: "ROOT#3454",
    },
  ];
</script>

<div class="w-screen h-screen overflow-hidden flex flex-row layout-container">
  <NavigationBar {navigationItems} footer={Me}></NavigationBar>
  <div class="w-full flex flex-col">
    <div class="w-full p-4">
      <SearchInput></SearchInput>
    </div>
    <div class="w-full overflow-auto hide-scrollbar">
      <div
        class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 px-2"
      >
        {#each rooms as room}
          <Room
            roomName={room.roomName}
            playListName={room.playListName}
            playListCount={room.playListCount}
            master={room.roomMaster}
          ></Room>
        {/each}
      </div>
    </div>
  </div>
</div>
{#if isCreatingRoom}
  <div
    class="absolute w-screen h-screen left-0 top-0 p-100 z-10 dimmed flex flex-col justify-center items-center"
  >
    <div class="w-1/2 min-w-max">
      <RoomCreate on:close={() => (isCreatingRoom = false)}></RoomCreate>
    </div>
  </div>
{/if}

<style>
  .dimmed {
    background-color: var(--translucent-background-color);
  }
</style>
