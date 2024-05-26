<template>
    <v-sheet class="w-screen h-screen d-flex flex-column justify-center align-center">
      <v-sheet class="text-h4 font-weight-bold mb-3">{{title}}</v-sheet>
      <v-sheet class="mb-8">{{info_message}}</v-sheet>
      <LoginServiceButton label="Discord로 로그인"><DiscordIcon></DiscordIcon></LoginServiceButton>
      <LoginServiceButton label="네이버로 로그인"><DiscordIcon></DiscordIcon></LoginServiceButton>
      <LoginServiceButton label="Google로 로그인"><DiscordIcon></DiscordIcon></LoginServiceButton>
    </v-sheet>
</template>

<script lang="ts">
import DiscordIcon from "@/icons/DiscordIcon.vue";
import LoginServiceButton from "@/components/login/LoginServiceButton.vue";

export default {
  components: {
    DiscordIcon,
    LoginServiceButton,
  },
    props: {
      title: {
        type: String,
        required: true,
      },
      info_message: {
        type: String,
        required: true,
      },
    },
  methods: {
    onClickBack(){
      window.location.href = "/";
    }
  }
  };
</script>

<style>
.discord-button {
  background-color: var(--primary-400);
  color: var(--font-light);
}
</style>