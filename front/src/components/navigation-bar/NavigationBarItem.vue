<template>
  <v-sheet
    class="w-100 my-1 d-flex flex-column text-center justify-center align-center navigation-bar-item"
  >
    <v-sheet :width="80" :height="80" class="flex-grow-0 flex-shrink-0">
      <slot></slot>
    </v-sheet>
    <v-sheet class="w-full">{{ label }}</v-sheet>
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
