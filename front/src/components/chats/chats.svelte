<script lang="ts">
  import PlayerChat from "./player-chat.svelte";
  import SystemChat from "./system-chat.svelte";

  export let chats = [
    {
      type: "System",
      contents: ["Hassium#0846님이 입장하셨습니다."],
      photoUrl: null,
      nickname: null,
    },
    {
      type: "Player",
      contents: ["안녕하세요.", "재밌어 보이네요!"],
      photoUrl: "/favicon.png",
      nickname: "Hassium#0846",
    },
  ];
</script>

<div class="card block p-2 chats">
  {#each chats as chat}
    {#if chat.type === "System"}
      <SystemChat contents={chat.contents}></SystemChat>
    {:else if chat.type === "Player" && chat.photoUrl && chat.nickname}
      <PlayerChat
        photoUrl={chat.photoUrl}
        nickname={chat.nickname}
        contents={chat.contents}
      ></PlayerChat>
    {/if}
  {/each}
</div>

<style>
  .chats {
    background-color: #1c2626;
    color: #e0e3e2;
  }
</style>
