<template>
  <v-sheet :width="112" class="h-screen py-4 d-flex flex-column align-center">
    <slot></slot>
  </v-sheet>
</template>

<style lang="scss" scoped>
.d-flex {
  gap: 4px;
}
</style>
