<template>
  <v-sheet class="rounded-lg w-100 h-100 pa-2 room-playlist">
    <v-sheet class="pa-2 d-flex flex-column">
      <v-sheet class="d-flex flex-row">
        <v-sheet :width="32" class="flex-grow-0 flex-shrink-0">
          <MusicNoteIcon></MusicNoteIcon>
        </v-sheet>
        <v-sheet class="ml-2 text-h4 font-weight-bold">{{ title }}</v-sheet>
      </v-sheet>
      <v-sheet class="px-2 d-flex flex-row">
        <v-sheet width="28" class="flex-grow-0 flex-shrink-0">
          <PlaylistMakerIcon></PlaylistMakerIcon>
        </v-sheet>
        <v-sheet class="ml-2 text-h5 font-weight-bold">{{ maker }}</v-sheet>
      </v-sheet>
      <v-sheet class="pa-2">
        <p>{{ comment }}</p>
      </v-sheet>
    </v-sheet>
  </v-sheet>
</template>

<script lang="ts">
import MusicNoteIcon from "@/icons/MusicNoteIcon.vue";
import PlaylistMakerIcon from "@/icons/PlaylistMakerIcon.vue";

export default {
  components: {
    MusicNoteIcon,
    PlaylistMakerIcon,
  },
  props: {
    title: {
      required: true,
      type: String,
    },
    maker: {
      required: true,
      type: String,
    },
    comment: {
      required: true,
      type: String,
    },
  },
};
</script>

<style>
.room-playlist {
  background-color: var(--primary-50);
  color: var(--font-light);
}
</style>
