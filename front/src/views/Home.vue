<template>
  <v-sheet class="w-100 h-100 overflow-hidden d-flex flex-row">
    <NavigationBar>
      <NavigationBarItem label="방 만들기"
        ><RoomCreateIcon></RoomCreateIcon
      ></NavigationBarItem>
    </NavigationBar>
    <div class="w-100">hello</div>
  </v-sheet>
</template>

<script lang="ts">
import NavigationBar from "@/components/navigation-bar/NavigationBar.vue";
import NavigationBarItem from "@/components/navigation-bar/NavigationBarItem.vue";
import RoomCreateIcon from "@/icons/RoomCreateIcon.vue";

export default {
  components: {
    NavigationBar,
    NavigationBarItem,
    RoomCreateIcon
  }
}
</script>

<style></style>
