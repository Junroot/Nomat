<template>
  <button class="d-flex flex-row align-center py-1 player">
    <v-avatar :image="photoUrl" size="48"></v-avatar>
    <span class="ml-1 px-1 v-flex flex-row">{{ nickname }}</span>
    <v-sheet :width="16" v-if="isMaster" class="ml-1"
      ><CrownIcon></CrownIcon
    ></v-sheet>
  </button>
</template>

<script lang="ts">
import CrownIcon from "@/icons/CrownIcon.vue";

export default {
  components: {
    CrownIcon,
  },
  props: {
    photoUrl: {
      required: true,
      type: String,
    },
    nickname: {
      required: true,
      type: String,
    },
    isMaster: {
      required: true,
      type: Boolean,
      default: false,
    },
  },
};
</script>

<style>
.player:hover {
  background-color: var(--primary-300);
  color: var(--font-dark);
}

.player:hover svg {
  fill: var(--font-dark);
}
</style>
