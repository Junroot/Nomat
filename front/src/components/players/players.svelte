<script lang="ts">
  import Player from "./player.svelte";

  var maxPlayers: number = 12;
  var players = [
    {
      nickname: "ROOT#3465",
      isMaster: true,
    },
    {
      nickname: "Hassium#0846",
      isMaster: false,
    },
  ];
</script>

<div class="card block p-1 players w-full h-full">
  <header class="card-header p-2">
    <div class="flex flex-row items-end">
      <div class="p-1 h2">플레이어</div>
      <div class="p-1 h4">{players.length}/{maxPlayers}</div>
    </div>
  </header>
  <section>
    <div class="flex flex-col gap-1">
      {#each players as player}
        <Player
          photoUrl="/favicon.png"
          nickname={player.nickname}
          isMaster={player.isMaster}
        ></Player>
      {/each}
    </div>
  </section>
</div>

<style>
  .players {
    background-color: #115552;
    color: #e0e3e2;
  }
</style>
