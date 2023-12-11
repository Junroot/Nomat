<template>
  <v-sheet class="w-100 h-100 overflow-hidden d-flex flex-row">
    <NavigationBar>
      <NavigationBarItem v-on:click="onClickLobby" label="대기실"
        ><ArrowBackIcon></ArrowBackIcon
      ></NavigationBarItem>
      <NavigationBarItem label="시작"><PlayIcon></PlayIcon></NavigationBarItem>
    </NavigationBar>
    <div class="w-100">hello</div>
  </v-sheet>
</template>

<script lang="ts">
import NavigationBar from "@/components/navigation-bar/NavigationBar.vue";
import NavigationBarItem from "@/components/navigation-bar/NavigationBarItem.vue";
import ArrowBackIcon from "@/icons/ArrowBackIcon.vue";
import PlayIcon from "@/icons/PlayIcon.vue";

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    ArrowBackIcon,
    PlayIcon,
  },
  methods: {
    onClickLobby() {
      window.location.href = "/";
    },
  },
};
</script>
