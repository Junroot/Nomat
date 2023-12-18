<template>
  <button
    style="width: 80px; height: 80px"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <v-sheet
      :height="80"
      class="d-flex flex-row align-center justify-center profile rounded-pill"
      :elevation="16"
    >
      <v-sheet class="flex-grow-0 flex-shrink-0" :width="80">
        <v-sheet class="ma-auto" :width="64">
          <UserIcon></UserIcon>
        </v-sheet>
      </v-sheet>
      <v-sheet
        v-if="isHover"
        class="flex-grow-0 flex-shrink-0 pr-8 text-h4 font-weight-medium nickname"
        >{{ nickname }}</v-sheet
      >
    </v-sheet>
  </button>
</template>

<script lang="ts">
import UserIcon from "@/icons/UserIcon.vue";

export default {
  components: {
    UserIcon,
  },
  props: {
    nickname: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      isHover: false,
    };
  },
  methods: {
    onMouseEnter() {
      this.isHover = true;
    },
    onMouseLeave() {
      this.isHover = false;
    },
  },
};
</script>

<style>
button {
  position: relative;
}

.profile {
  position: absolute;
  top: 0;
  transition: all 0.3s;
}

.profile:hover {
  background-color: var(--tertiary-100);
}
</style>
