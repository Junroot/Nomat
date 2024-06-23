<template>
    <v-sheet class="w-screen h-screen d-flex flex-column justify-center align-center">
      <v-sheet class="text-h4 font-weight-bold mb-3">{{title}}</v-sheet>
      <v-sheet class="mb-6">{{info_message}}</v-sheet>
      <v-sheet class="d-flex flex-row ga-6">
        <LoginServiceButton><DiscordIcon></DiscordIcon></LoginServiceButton>
        <LoginServiceButton><NaverIcon></NaverIcon></LoginServiceButton>
        <LoginServiceButton><GoogleIcon></GoogleIcon></LoginServiceButton>
      </v-sheet>
    </v-sheet>
</template>

<script lang="ts">
import DiscordIcon from "@/icons/DiscordIcon.vue";
import NaverIcon from "@/icons/NaverIcon.vue";
import GoogleIcon from "@/icons/GoogleIcon.vue";
import LoginServiceButton from "@/components/login/LoginServiceButton.vue";

export default {
  components: {
    DiscordIcon,
    NaverIcon,
    GoogleIcon,
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