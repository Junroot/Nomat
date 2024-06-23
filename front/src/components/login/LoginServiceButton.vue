<template>
    <v-sheet class="rounded-lg">
        <v-sheet class="d-flex flex-row mb-7">
            <v-sheet :width="60" class="flex-grow-0 flex-shrink-0 mr-2">
                <slot></slot>
            </v-sheet>
        </v-sheet>
    </v-sheet>
</template>

<style>
.discord-button {
  background-color: var(--primary-400);
  color: var(--font-light);
}
</style>