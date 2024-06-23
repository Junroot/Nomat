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
          room?.title || ""
        }}</v-sheet>
        <v-sheet class="my-1 flex-0-0"
          ><RoomPlaylist
            :title="room?.playlist.name || ''"
            :maker="room?.playlist.master || ''"
            :comment="room?.playlist.comment || ''"
          ></RoomPlaylist
        ></v-sheet>
        <v-sheet class="my-1 flex-0-1">
          <Players :players="room?.players || []" :max-players="20"></Players>
        </v-sheet>
      </v-sheet>
      <v-sheet class="pa-2 d-flex flex-column second-col">
        <Chats :chats="chats"></Chats>
        <v-sheet class="w-100 mt-2 mb-4"
          ><ChatInput class="mt-2"></ChatInput
        ></v-sheet>
      </v-sheet>
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
import Chats from "@/components/room/chats/Chats.vue";
import ChatInput from "@/components/room/chats/ChatInput.vue";
import { PlayerResponse } from "@/components/room/players/PlayerData";
import axios from "@/plugins/axios";

type RoomResponse = {
  id: number;
  title: string;
  playlist: {
    id: number;
    name: string;
    count: number;
    master: string;
    comment: string;
  };
  players: PlayerResponse[];
};

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    RoomPlaylist,
    ArrowBackIcon,
    PlayIcon,
    Players,
    Chats,
    ChatInput
  },
  data() {
    return {
      roomId: this.$route.params.id,
      room: null as RoomResponse | null,
      chats: [
        {
          type: "System",
          contents: ["Hassium#0846님이 입장하셨습니다."],
          photoUrl: null,
          nickname: null
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        },
        {
          type: "Player",
          contents: ["안녕하세요.", "재밌어 보이네요!"],
          photoUrl: "/favicon.ico",
          nickname: "Hassium#0846"
        }
      ]
    };
  },
  created() {
    axios
      .get(`/rooms/${this.roomId}`)
      .then((response: { status: number; data: RoomResponse }) => {
        if (response.status == 200) {
          this.room = response.data;
        }
      });
  },
  methods: {
    onClickLobby() {
      window.location.href = "/";
    }
  }
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
