<template>
  <v-sheet class="rounded-lg room-create pa-12">
    <v-sheet class="pa-2 d-flex flex-column ga-2 justify-center">
      <v-sheet class="text-h4 font-weight-bold mb-4">방 만들기</v-sheet>
      <v-form ref="form">
        <v-sheet class="d-flex flex-column ga-1">
          <span>방 이름</span>
          <v-text-field
            v-model="roomName"
            clearable
            variant="solo"
            placeholder="1자 이상 입력"
            bg-color="var(--primary-100)"
            base-color="var(--font-dark)"
            color="var(--font-dark)"
            density="compact"
            rounded
            hide-details
            single-line
            flat
            :rules="[isValidRoomName]"
          />
        </v-sheet>
        <v-sheet class="d-flex flex-column ga-1 justify-center">
          <span>최대 인원수</span>
          <v-select
            :items="Array.from({ length: maxRoomCapacity }, (_, i) => i + 1)"
            v-model="selectedRoomCapacity"
            variant="solo"
            bg-color="var(--primary-100)"
            base-color="var(--font-dark)"
            density="compact"
            rounded
            hide-details
            single-line
            flat
          >
          </v-select>
        </v-sheet>
        <v-sheet class="d-flex flex-column ga-1 justify-center">
          <span>비밀번호</span>
          <div class="d-flex flex-row ga-2">
            <div>
              <v-switch
                v-model="usePassword"
                @update:model-value="onChangeUsePassword"
                inset
                flat
                density="compact"
                color="var(--primary-200)"
                hide-details
              ></v-switch>
            </div>
            <v-sheet class="d-flex align-center flex-1-1">
              <v-text-field
                v-model="password"
                :disabled="!usePassword"
                type="password"
                clearable
                variant="solo"
                bg-color="var(--primary-100)"
                base-color="var(--font-dark)"
                color="var(--font-dark)"
                density="compact"
                rounded
                hide-details
                single-line
                flat
                :rules="[isValidPassword]"
              />
            </v-sheet>
          </div>
        </v-sheet>
        <v-sheet class="d-flex flex-column ga-1 justify-center">
          <span>플레이리스트</span>
          <v-autocomplete
            :items="playlists"
            @update:search="onSearch"
            @update:model-value="onSelect"
            variant="solo"
            bg-color="var(--primary-100)"
            base-color="var(--font-dark)"
            class="flex-full-width"
            :custom-filter="customFilter"
            density="compact"
            menu-icon=""
            rounded
            flat
            :rules="[isValidPlaylist]"
          ></v-autocomplete>
        </v-sheet>
        <v-sheet class="d-flex flex-row ga-2 justify-center w-100">
          <v-sheet class="flex-1-1"
            ><v-btn
              block
              rounded
              flat
              variant="tonal"
              :disabled="!isValidForm"
              class="create-button"
              >만들기</v-btn
            ></v-sheet
          >
          <v-sheet class="flex-1-1"
            ><v-btn
              block
              rounded
              flat
              @click="$emit('close')"
              class="cancel-button"
              >취소</v-btn
            ></v-sheet
          >
        </v-sheet>
      </v-form>
    </v-sheet>
  </v-sheet>
</template>

<script lang="ts">
import { VForm } from "vuetify/lib/components/index.mjs";

export default {
  data() {
    return {
      roomName: "",
      maxRoomCapacity: 20,
      selectedRoomCapacity: 1,
      usePassword: false,
      password: "",
      playlists: [] as Array<any>,
      selectedPlaylist: null as number | null,
      searchingId: null as NodeJS.Timeout | null,
    };
  },
  mounted() {
    this.validateForm();
  },
  computed: {
    isValidRoomName() {
      return !!this.roomName && this.roomName.trim().length > 0;
    },
    isValidPassword() {
      return !this.usePassword || (!!this.password && this.password.length > 0);
    },
    isValidPlaylist() {
      return this.selectedPlaylist !== null;
    },
    isValidForm() {
      return (
        this.isValidRoomName && this.isValidPassword && this.isValidPlaylist
      );
    },
  },
  watch: {
    usePassword(newUsePassword) {
      if (!newUsePassword) {
        this.password = "";
      }
    },
  },
  methods: {
    async onSearch() {
      setTimeout(() => {
        if (this.searchingId !== null) {
          clearTimeout(this.searchingId);
        }
        this.searchingId = setTimeout(() => {
          this.playlists = [
            {
              value: 1,
              title: "헉1",
            },
            {
              value: 2,
              title: "헉2",
            },
            {
              value: 3,
              title: "헉3",
            },
            {
              value: 4,
              title: "헉4",
            },
            {
              value: 5,
              title: "헉5",
            },
          ];
        }, 250);
      }, 1000);
    },
    async onSelect(value: any) {
      this.selectedPlaylist = value;
    },
    onChangeUsePassword() {
      this.validateForm();
    },
    customFilter() {
      return true;
    },

    validateForm() {
      let form = this.$refs.form as VForm;
      form?.validate();
    },
  },
};
</script>

<style>
.room-create {
  background-color: var(--primary-50);
}

.v-select__content {
  background-color: var(--primary-100);
}

.v-autocomplete__content {
  background-color: var(--primary-100);
}

.v-list-item__overlay {
  background-color: #00000000 !important;
}

.v-list-item:hover {
  background-color: var(--primary-200);
}

.v-input--disabled .v-field__overlay {
  background-color: var(--primary-50);
}

.v-input--error .v-field__overlay {
  border: solid var(--error-300);
}

.create-button {
  background-color: var(--secondary) !important;
  color: var(--font-dark) !important;
}

.create-button .v-btn__underlay {
  background-color: #00000000 !important;
}

.create-button .v-btn__overlay {
  background-color: #00000000 !important;
}

.cancel-button {
  background-color: var(--primary-200) !important;
  color: var(--font-light) !important;
}

.cancel-button .v-btn__overlay {
  background-color: #00000000 !important;
}
</style>
