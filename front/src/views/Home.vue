<template>
  <v-sheet class="w-screen h-screen overflow-hidden d-flex flex-row">
    <NavigationBar>
      <v-sheet class="flex-grow-1 flex-shrink-1">
        <NavigationBarItem label="방 만들기" :onclick="onClickCreateRoom">
          <RoomCreateIcon></RoomCreateIcon>
        </NavigationBarItem>
      </v-sheet>
      <v-sheet class="flex-grow-0 flex-shrink-0"
        ><Me nickname="ROOT#3465"></Me
      ></v-sheet>
    </NavigationBar>
    <v-sheet class="w-100 d-flex flex-column">
      <v-sheet class="w-100 pa-4"
        ><SearchInput class="mt-2"></SearchInput
      ></v-sheet>
      <v-container
        class="w-100 px-4 overflow-y-auto hide-scrollbar"
        :fluid="true"
      >
        <v-row>
          <v-col
            v-for="room in rooms"
            :key="room.id"
            cols="12"
            md="6"
            xl="4"
            xxl="3"
          >
            <Room
              :room-id="room.id"
              :room-name="room.title"
              :play-list-name="room.playlist.name"
              :playlist-count="room.playlist.count"
              :master="room.masterNickname"
            ></Room>
          </v-col>
        </v-row>
      </v-container>
    </v-sheet>
    <v-overlay
      v-model="isRoomCreation"
      scrim="black"
      width="50%"
      class="align-center justify-center"
    >
      <RoomCreate @close="() => (isRoomCreation = false)"></RoomCreate>
    </v-overlay>
  </v-sheet>
</template>

<script lang="ts">
import NavigationBar from "@/components/navigation-bar/NavigationBar.vue";
import NavigationBarItem from "@/components/navigation-bar/NavigationBarItem.vue";
import RoomCreateIcon from "@/icons/RoomCreateIcon.vue";
import Room from "@/components/lobby/Room.vue";
import SearchInput from "@/components/lobby/SearchInput.vue";
import Me from "@/components/lobby/Me.vue";
import RoomCreate from "@/components/lobby/RoomCreate.vue";
import axios from "@/plugins/axios";

type RoomResponse = {
  id: number;
  title: string;
  playlist: {
    id: number;
    name: string;
    count: number;
  };
  masterNickname: string;
};

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    RoomCreateIcon,
    Room,
    SearchInput,
    Me,
    RoomCreate
  },
  data() {
    return {
      isRoomCreation: false,
      rooms: [] as RoomResponse[]
    };
  },
  created() {
    axios
      .get("/rooms")
      .then((response: { status: number; data: RoomResponse[] }) => {
        if (response.status == 200) {
          this.rooms = response.data;
        }
      });
  },
  methods: {
    onClickCreateRoom() {
      this.isRoomCreation = true;
    }
  }
};
</script>

<style></style>
