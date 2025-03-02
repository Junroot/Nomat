<template>
  <v-sheet class="w-100 h-100 pa-2 rounded-lg room" @click="moveToRoom">
    <v-sheet class="pa-2 text-h4 font-weight-bold">{{ roomName }}</v-sheet>
    <v-sheet class="pa-2 d-flex flex-column ga-1">
      <v-sheet class="px-3 d-flex flex-row align-center ga-1">
        <v-sheet class="flex-grow-0 flex-shrink-0" :width="32" :height="32">
          <PlaylistIcon></PlaylistIcon>
        </v-sheet>
        <p>{{ playListName }}</p>
      </v-sheet>
      <v-sheet class="px-3 d-flex flex-row align-center ga-1">
        <v-sheet class="flex-grow-0 flex-shrink-0" :width="32" :height="32">
          <MusicIcon></MusicIcon>
        </v-sheet>
        <p>{{ playlistCount }}</p>
      </v-sheet>
      <v-sheet class="px-3 d-flex flex-row align-center ga-1">
        <v-sheet class="flex-grow-0 flex-shrink-0" :width="32" :height="32">
          <UserIcon></UserIcon>
        </v-sheet>
        <p>{{ master }}</p>
      </v-sheet>
    </v-sheet>
  </v-sheet>
</template>

<script lang="ts">
import PlaylistIcon from "@/icons/PlaylistIcon.vue";
import MusicIcon from "@/icons/MusicIcon.vue";
import UserIcon from "@/icons/UserIcon.vue";

export default {
  components: {
    PlaylistIcon,
    MusicIcon,
    UserIcon
  },
  props: {
    roomId: {
      type: Number,
      required: true
    },
    roomName: {
      type: String,
      required: true
    },
    playListName: {
      type: String,
      required: true
    },
    playlistCount: {
      type: Number,
      required: true
    },
    master: {
      type: String,
      required: true
    }
  },
  methods: {
    moveToRoom: function() {
      location.replace(`/rooms/${this.roomId}`);
    }
  }
};
</script>

<style>
.room {
  cursor: pointer;
  background-color: var(--primary-100);
}

a {
  color: var(--font-light);
  text-decoration: none;
}
</style>
