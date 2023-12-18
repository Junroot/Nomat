<template>
  <v-sheet class="w-screen h-screen overflow-hidden d-flex flex-row">
    <NavigationBar>
      <NavigationBarItem label="방 만들기"
        ><RoomCreateIcon></RoomCreateIcon
      ></NavigationBarItem>
    </NavigationBar>
    <v-container
      class="w-100 pa-0 ma-2 overflow-y-auto hide-scrollbar"
      :fluid="true"
    >
      <v-row class="w-100">
        <v-col v-for="n in 24" :key="n" cols="12" md="6" xl="4" xxl="3">
          <Room
            room-id="1"
            room-name="들어오셈"
            play-list-name="오늘의 TOP 100: 일본"
            :playlist-count="100"
            master="ROOT#34565"
          ></Room>
        </v-col>
      </v-row>
    </v-container>
  </v-sheet>
</template>

<script lang="ts">
import NavigationBar from "@/components/navigation-bar/NavigationBar.vue";
import NavigationBarItem from "@/components/navigation-bar/NavigationBarItem.vue";
import RoomCreateIcon from "@/icons/RoomCreateIcon.vue";
import Room from "@/components/lobby/Room.vue";

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    RoomCreateIcon,
    Room,
  },
};
</script>

<style></style>
