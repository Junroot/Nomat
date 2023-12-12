<template>
  <v-sheet class="w-screen h-screen overflow-hidden d-flex flex-row">
    <NavigationBar>
      <NavigationBarItem v-on:click="onClickLobby" label="대기실"
        ><ArrowBackIcon></ArrowBackIcon
      ></NavigationBarItem>
      <NavigationBarItem label="시작"><PlayIcon></PlayIcon></NavigationBarItem>
    </NavigationBar>
    <v-sheet class="w-100 d-flex flex-row">
      <v-sheet
        class="pa-2 h-100 d-flex flex-column overflow-auto hide-scrollbar first-col"
      >
        <v-sheet class="text-h2 pa-2 flex-0-0 font-weight-bold">{{
          roomName
        }}</v-sheet>
        <v-sheet class="my-1 flex-0-0"
          ><RoomPlaylist
            :title="playlistName"
            :maker="playlistMaker"
            :comment="playlistComment"
          ></RoomPlaylist
        ></v-sheet>
        <v-sheet class="my-1 flex-0-1">
          <Players :players="players" :max-players="20"></Players>
        </v-sheet>
      </v-sheet>
      <v-sheet class="pa-2 second-col">sdf</v-sheet>
    </v-sheet>
  </v-sheet>
</template>

<script lang="ts">
import NavigationBar from "@/components/navigation-bar/NavigationBar.vue";
import NavigationBarItem from "@/components/navigation-bar/NavigationBarItem.vue";
import RoomPlaylist from "@/components/room/RoomPlaylist.vue";
import Players from "@/components/room/players/Players.vue";
import ArrowBackIcon from "@/icons/ArrowBackIcon.vue";
import PlayIcon from "@/icons/PlayIcon.vue";

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    RoomPlaylist,
    ArrowBackIcon,
    PlayIcon,
    Players,
  },
  data: function () {
    return {
      roomName: "들어오셈",
      playlistName: "오늘의 TOP 100: 일본",
      playlistMaker: "ROOT#3465",
      playlistComment:
        "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!",
      players: [
        {
          nickname: "ROOT#3454",
          photoUrl: "/favicon.ico",
          isMaster: true,
        },
        {
          nickname: "Hassium#0846",
          photoUrl: "/favicon.ico",
          isMaster: false,
        },
      ],
    };
  },
  methods: {
    onClickLobby() {
      window.location.href = "/";
    },
  },
};
</script>

<style>
.first-col {
  flex-basis: 33.333333%;
}
.second-col {
  flex-basis: 66.666666%;
}
</style>
