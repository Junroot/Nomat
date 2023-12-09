<script lang="ts">
  import { createEventDispatcher } from "svelte";
  import { SlideToggle, Autocomplete } from "@skeletonlabs/skeleton";
  import type { AutocompleteOption } from "@skeletonlabs/skeleton";

  const dispatch = createEventDispatcher();

  let maxRoomCapacity = 20;
  let passwordEnabled = false;
  let name = "";
  let password = "";
  let roomCapacity = 2;
  let playListSearchValue = "";
  let isEnableAutoComplete = false;
  let selectedPlayListId: string | null = null;

  $: isValidName = name.trim().length > 0;
  $: isValidPassword = !passwordEnabled || password.length !== 0;
  $: isValidPlayList = selectedPlayListId !== null;
  $: isValidInput = isValidName && isValidPassword && isValidPlayList;

  let options: AutocompleteOption<string>[] = [
    {
      label: "오늘의 TOP 100: 일본",
      value: "id1",
    },
    {
      label: "오늘의 TOP 100: 한국",
      value: "id2",
    },
    {
      label: "오늘의 TOP 100: 미국",
      value: "id3",
    },
    {
      label: "오늘의 TOP 100: 일본",
      value: "id1",
    },
    {
      label: "오늘의 TOP 100: 한국",
      value: "id2",
    },
    {
      label: "오늘의 TOP 100: 미국",
      value: "id3",
    },
    {
      label: "오늘의 TOP 100: 일본",
      value: "id1",
    },
    {
      label: "오늘의 TOP 100: 한국",
      value: "id2",
    },
    {
      label: "오늘의 TOP 100: 미국",
      value: "id3",
    },
  ];

  function onPlayListSelection(
    event: CustomEvent<AutocompleteOption<string>>,
  ): void {
    playListSearchValue = event.detail.label;
    selectedPlayListId = event.detail.value;
    isEnableAutoComplete = false;
  }
</script>

<div class="card p-12 room-create">
  <header class="card-header p-0 mb-4 h2">방 만들기</header>
  <section>
    <div class="flex flex-col gap-1">
      <div>
        <span>방 이름</span>
        <input
          class="input px-4"
          class:red-border={!isValidName}
          type="text"
          bind:value={name}
          placeholder="1자 이상 입력"
          on:input={() => {
            console.log(name.trim.length);
            console.log(isValidName);
          }}
        />
      </div>
      <div>
        <span>최대 인원수</span>
        <select class="select rounded-full px-4" bind:value={roomCapacity}>
          {#each Array(maxRoomCapacity) as _, index}
            <option value={index + 1}>{index + 1}</option>
          {/each}
        </select>
      </div>
      <div>
        <span>비밀번호</span>
        <div class="flex flex-row gap-2 items-center">
          <SlideToggle
            name="password"
            active="bg-primary-400"
            on:click={() => {
              password = "";
              passwordEnabled = !passwordEnabled;
            }}
            checked={passwordEnabled}
          />
          <input
            class="input px-4"
            class:red-border={!isValidPassword}
            type="password"
            bind:value={password}
            disabled={!passwordEnabled}
          />
        </div>
      </div>
      <div class="relative">
        <span>플레이리스트</span>
        <input
          class="input px-4"
          class:red-border={!isValidPlayList}
          type="search"
          bind:value={playListSearchValue}
          on:input={() => {
            isEnableAutoComplete = true;
            selectedPlayListId = null;
          }}
        />
        {#if isEnableAutoComplete}
          <div
            class="absolute mt-2 card z-20 w-full p-4 overflow-y-auto auto-complete"
          >
            <Autocomplete
              bind:input={playListSearchValue}
              {options}
              filter={() => {
                return options;
              }}
              on:selection={onPlayListSelection}
            />
          </div>
        {/if}
      </div>
    </div>
  </section>
  <footer class="card-footer mt-12 p-0 flex flex-row gap-4">
    <button
      class="btn flex-1 create-button font-bold"
      disabled={!isValidInput}
      on:click={() => {
        console.log(name);
        console.log(passwordEnabled);
        console.log(password);
        console.log(roomCapacity);
      }}>만들기</button
    >
    <button
      class="btn flex-1 cancel-button font-bold"
      on:click={() => dispatch("close")}>취소</button
    >
  </footer>
</div>

<style>
  .room-create {
    background-color: var(--primary-100);
    color: var(--font-light);
  }

  .input {
    background-color: var(--tertiary-50);
    border: solid transparent;
  }

  .select {
    background-color: var(--tertiary-50);
    border: solid transparent;
  }

  .auto-complete {
    background-color: var(--tertiary-50);
  }

  .create-button {
    background-color: var(--primary-300);
    color: var(--font-dark);
  }

  .cancel-button {
    background-color: var(--tertiary-50);
  }

  .red-border {
    border: solid var(--error-300);
  }
</style>
