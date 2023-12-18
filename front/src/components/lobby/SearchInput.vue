<template>
  <v-sheet class="w-100">
    <v-text-field
      clearable
      placeholder="방 이름, 플레이리스트, 플레이어 이름으로 검색"
      variant="solo"
      class="w-100 h-100"
      bg-color="var(--tertiary-100)"
      base-color="var(--font-dark)"
      color="var(--font-dark)"
      density="comfortable"
      rounded
      single-line
      hide-details
    >
      <slot:prepend-inner>
        <v-sheet :width="48" :height="24"><SearchIcon></SearchIcon></v-sheet>
      </slot:prepend-inner>
    </v-text-field>
  </v-sheet>
</template>

<script lang="ts">
import SearchIcon from "@/icons/SearchIcon.vue";

export default {
  components: {
    SearchIcon,
  },
};
</script>
