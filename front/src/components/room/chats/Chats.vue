<template>
  <v-sheet
    class="pa-4 w-100 h-100 rounded-lg d-flex flex-column overflow-y-auto chats hide-scrollbar"
  >
    <Chat v-for="(chat, i) in chats" :key="i" :chat-data="chat"></Chat>
  </v-sheet>
</template>

<script lang="ts">
import type { ChatData } from "@/components/room/chats/ChatData";
import Chat from "./Chat.vue";

export default {
  components: {
    Chat,
  },
  props: {
    chats: {
      type: Array<ChatData>,
      required: true,
    },
  },
};
</script>

<style>
.chats {
  background-color: var(--tertiary-50);
}
</style>
