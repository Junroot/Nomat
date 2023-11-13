<div class="card block p-2 players">
    <header class="card-header">
        <div class="flex flex-row items-end">
            <div class="p-1 h2">플레이어</div>
            <div class="p-1 h4">{players.length}/{maxPlayers}</div>
        </div>
    </header>
    <section>
        <div class="flex flex-col p-1">
            {#each players as player}
                <Player photoUrl="/favicon.png" nickname={player.nickname} isMaster={player.isMaster}></Player>
            {/each}
        </div>
    </section>
</div>

<script>
    import Player from "./player.svelte";

    var maxPlayers = 12;
    var players = [
        {
            "nickname": "ROOT#3465",
            "isMaster": true,
        },
        {
            "nickname": "Hassium#0846",
            "isMaster": false
        }
    ];
</script>

<style>
    .players {
        background-color: #115552;
    }
</style>