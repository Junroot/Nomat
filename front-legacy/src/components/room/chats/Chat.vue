<template>
  <div v-if="chatData.type === 'System'">
    <p
      v-for="(content, i) in chatData.contents"
      :key="i"
      class="font-weight-bold"
    >
      {{ content }}
    </p>
  </div>
  <div
    v-if="chatData.type === 'Player' && chatData.photoUrl && chatData.nickname"
  >
    <v-sheet class="d-flex flex-row pa-1">
      <v-avatar :image="chatData.photoUrl" size="48"></v-avatar>
      <v-sheet class="d-flex flex-column px-2">
        <v-sheet class="font-weight-bold">{{ chatData.nickname }}</v-sheet>
        <v-sheet v-for="(content, i) in chatData.contents" :key="i">
          {{ content }}
        </v-sheet>
      </v-sheet>
    </v-sheet>
  </div>
</template>

<script lang="ts">
import type { ChatData } from "@/components/room/chats/ChatData";
import { PropType } from "vue";

export default {
  props: {
    chatData: {
      type: Object as PropType<ChatData>,
      required: true,
    },
  },
};
</script>
