<template>
  <v-sheet class="pa-2 w-100 h-100 rounded-lg players d-flex flex-column">
    <v-sheet class="pa-2 d-flex flex-row align-end flex-0-0">
      <v-sheet class="text-h4 font-weight-bold">플레이어</v-sheet>
      <v-sheet class="ml-2 text-h6"
        >{{ players.length }}/{{ maxPlayers }}</v-sheet
      >
    </v-sheet>
    <v-sheet class="pa-2 flex-1-1 d-flex flex-column">
      <Player
        v-for="player in players"
        :nickname="player.nickname"
        :is-master="player.isMaster"
        :photo-url="player.photoUrl"
        :key="player.nickname"
      ></Player>
    </v-sheet>
  </v-sheet>
</template>
PlayerData
<script lang="ts">
import type { PlayerData } from "@/components/room/players/PlayerData";
import Player from "@/components/room/players/Player.vue";

export default {
  components: {
    Player,
  },
  props: {
    maxPlayers: {
      required: true,
      type: Number,
    },
    players: {
      required: true,
      type: Array<PlayerData>,
    },
  },
};
</script>

<style>
.players {
  background-color: var(--tertiary-200);
}
</style>
