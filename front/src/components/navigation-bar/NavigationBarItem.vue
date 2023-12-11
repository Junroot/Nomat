<template>
  <v-sheet class="w-100 my-1 text-center justify-center navigation-bar-item">
    <v-sheet class="w-100 ha-24 d-flex flex-column">
      <v-sheet :width="80" :height="80" class="w-100 ha-16 flex-grow-0">
        <slot></slot>
      </v-sheet>
      <v-sheet class="w-full">{{ label }}</v-sheet>
    </v-sheet>
  </v-sheet>
</template>

<script lang="ts">
export default {
  props: {
    label: {
      type: String,
      required: true,
    },
  },
};
</script>

<style lang="scss" scoped>
.navigation-bar-item {
  cursor: pointer;
}
</style>
