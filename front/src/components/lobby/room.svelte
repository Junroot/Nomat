<script lang="ts">
  import MusicIcon from "../../icons/music-icon.svelte";
  import PlayListIcon from "../../icons/play-list-icon.svelte";
  import UserIcon from "../../icons/user-icon.svelte";

  export let roomName: string;
  export let playListName: string;
  export let playListCount: number;
  export let master: string;
</script>

<div class="card p-2 m-1 cursor-pointer overflow-hidden">
  <div class="p-2 h3 font-bold">{roomName}</div>
  <div class="p-2 flex flex-col gap-1">
    <div class="px-3 flex flex-row items-center gap-2">
      <div class="w-8 flex-none"><PlayListIcon></PlayListIcon></div>
      <p class="break-all">{playListName}</p>
    </div>
    <div class="px-3 flex flex-row items-center gap-2">
      <div class="w-8 flex-none"><MusicIcon></MusicIcon></div>
      <p class="break-all">#{playListCount}</p>
    </div>
    <div class="px-3 flex flex-row items-center gap-2">
      <div class="w-8 flex-none"><UserIcon></UserIcon></div>
      <p class="break-all">{master}</p>
    </div>
  </div>
</div>

<style>
  .card {
    background-color: var(--primary-100);
  }
</style>
