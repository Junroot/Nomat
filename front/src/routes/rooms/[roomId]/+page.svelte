<script>
  import ChatInput from "../../../components/chats/chat-input.svelte";
  import Chats from "../../../components/chats/chats.svelte";
  import Players from "../../../components/players/players.svelte";

  let roomName = "들어오셈";
</script>

<div class="w-screen h-screen overflow-hidden flex flex-row gap-2 p-4 room">
  <div class="basis-1/3 flex flex-col overflow-auto hide-scrollbar gap-2">
    <div class="h1 font-bold p-2">{roomName}</div>
    <div><Players></Players></div>
    <div class="h-full"><Players></Players></div>
  </div>
  <div class="basis-2/3 flex flex-col-reverse gap-2">
    <div class="h-16"><ChatInput></ChatInput></div>
    <div class="w-full h-full"><Chats></Chats></div>
  </div>
</div>

<style>
  .room {
    background-color: var(--background-color);
    color: var(--font-light);
  }
</style>
