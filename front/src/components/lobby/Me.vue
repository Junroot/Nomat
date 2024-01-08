<template>
  <button style="width: 80px; height: 80px">
    <v-sheet
      :height="defaultProfileWidth"
      :width="profileWidth"
      @mouseenter="onMouseEnter"
      @mouseleave="onMouseLeave"
      class="d-flex flex-row align-center profile rounded-pill"
      :class="{ 'elevation-16': isHover }"
    >
      <v-sheet class="flex-grow-0 flex-shrink-0" :width="defaultProfileWidth">
        <v-sheet class="ma-auto" :width="64">
          <UserIcon></UserIcon>
        </v-sheet>
      </v-sheet>
      <v-sheet
        ref="nickname"
        class="flex-grow-0 flex-shrink-0 pr-8 text-h4 font-weight-medium nickname"
        >{{ nickname }}</v-sheet
      >
    </v-sheet>
  </button>
</template>

<script lang="ts">
import UserIcon from "@/icons/UserIcon.vue";
import { VSheet } from "vuetify/lib/components/index.mjs";

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
      defaultProfileWidth: 80,
      profileWidth: 80,
    };
  },
  watch: {
    isHover(newIsHover) {
      if (!newIsHover) {
        this.profileWidth = this.defaultProfileWidth;
        return;
      }
      let nicknameRef = this.$refs.nickname as VSheet;
      if (!nicknameRef) {
        return;
      }
      this.profileWidth =
        nicknameRef.$el.clientWidth + this.defaultProfileWidth;
    },
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
  overflow: hidden;
  top: 0;
  transition: all 0.3s ease;
}

.profile:hover {
  background-color: var(--tertiary-100);
}

.nickname {
  display: inline-block;
}
</style>
