<template>
  <v-card
    ref="card"
    :class="['pip-card', visible ? 'visible' : '']"
    :style="cardStyle"
    @mousedown="startDrag"
  >
    <div class="overlay">
      <div v-if="isMuted" class="muted-overlay">
        클릭해서 음소거 해제
      </div>
    </div>
    <div ref="ytPlayer"></div>
  </v-card>
</template>
  
<script>
import { ref, computed, onMounted, onUnmounted } from "vue";

export default {
  setup() {
    const position = ref({ x: 12, y: 12 }); // 초기 위치
    const velocity = ref({ x: 0, y: 0 }); // 속도 (관성 효과)
    const isDragging = ref(false);
    const cardSize = ref({ width: 0, height: 0 });
    let lastMousePosition = { x: 0, y: 0 };
    let animationFrame = null;

    const cardStyle = computed(() => ({
      transform: `translate(${position.value.x}px, ${position.value.y}px)`,
    }));

    const updateCardSize = () => {
      const card = document.querySelector(".pip-card");
      if (card) {
        cardSize.value.width = card.offsetWidth;
        cardSize.value.height = card.offsetHeight;
      }
    };

    const startDrag = (event) => {
      isDragging.value = true;
      velocity.value = { x: 0, y: 0 }; // 초기 속도 리셋
      lastMousePosition = { x: event.clientX, y: event.clientY };

      document.addEventListener("mousemove", onDrag);
      document.addEventListener("mouseup", stopDrag);
    };

    const onDrag = (event) => {
      if (!isDragging.value) return;
      event.preventDefault(); // 불필요한 선택 방지

      const deltaX = event.clientX - lastMousePosition.x;
      const deltaY = event.clientY - lastMousePosition.y;

      position.value.x += deltaX;
      position.value.y += deltaY;

      velocity.value.x = deltaX;
      velocity.value.y = deltaY;

      lastMousePosition = { x: event.clientX, y: event.clientY };

      limitPosition();
    };

    const stopDrag = () => {
      isDragging.value = false;
      document.removeEventListener("mousemove", onDrag);
      document.removeEventListener("mouseup", stopDrag);
      applyInertia();
    };

    const applyInertia = () => {
      const friction = 0.95; // 감속 계수
      const animate = () => {
        if (Math.abs(velocity.value.x) < 0.5 && Math.abs(velocity.value.y) < 0.5) {
          cancelAnimationFrame(animationFrame);
          return;
        }

        velocity.value.x *= friction;
        velocity.value.y *= friction;

        position.value.x += velocity.value.x;
        position.value.y += velocity.value.y;

        limitPosition();

        animationFrame = requestAnimationFrame(animate);
      };

      animationFrame = requestAnimationFrame(animate);
    };

    const limitPosition = () => {
      const screenWidth = window.innerWidth;
      const screenHeight = window.innerHeight;
      const padding = 12;

      const minX = padding;
      const maxX = screenWidth - cardSize.value.width - padding;
      const minY = padding;
      const maxY = screenHeight - cardSize.value.height - padding;

      position.value.x = Math.max(minX, Math.min(maxX, position.value.x));
      position.value.y = Math.max(minY, Math.min(maxY, position.value.y));
    };

    onMounted(() => {
      updateCardSize();
      window.addEventListener("resize", limitPosition);
      window.addEventListener("resize", updateCardSize);
    });

    onUnmounted(() => {
      window.removeEventListener("resize", updateCardSize);
      window.removeEventListener("resize", limitPosition);
    });

    return {
      position,
      cardStyle,
      startDrag,
      cardSize,
    };
  },
  data() {
    return {
      ytPlayer: null,
      isMuted: true,
      playCount: 0,
      visible: false,
    }
  },
  mounted() {
    this.addUserInteractionListener();
    this.createPlayer("qsMGWds79Bc", 10, 13);
  },
  beforeUnmount() {
    this.removeUserInteractionListener();
  },
  methods: {
    loadYouTubeAPI(videoId, start, end) {
      const tag = document.createElement("script");
      tag.src = "https://www.youtube.com/iframe_api";
      tag.onload = () => {
        window.onYouTubeIframeAPIReady = () => this.createPlayer(videoId, start, end);
      };
      document.body.appendChild(tag);
    },
    createPlayer(videoId, start, end) {
      if (!window.YT) {
        this.loadYouTubeAPI(videoId, start, end);
        return;
      }

      if (this.ytPlayer !== null) {
        this.ytPlayer.destroy();
      }

      this.ytPlayer = new YT.Player(this.$refs.ytPlayer, {
        height: this.cardSize.height,
        width: this.cardSize.width,
        videoId: videoId,
        playerVars: {
          cc_load_policy: 0,
          controls: 0,
          disablekb: 1,
          iv_load_policy: 3,
          modestbranding: 1,
          fs: 0,
          rel: 0,
          showinfo: 0,
          playsinline: 1,
          start: start,
          end: end,
          autoplay: 1,
        },
        events: {
          onReady: this.onPlayerReady,
          onStateChange: this.onStateChange,
        }
      });
    },
    onPlayerReady(event) {
      event.target.mute();
      event.target.playVideo();
      if (!this.isMuted) {
        event.target.unMute();
      }
    },
    onStateChange(event) {
      if (event.data === 0) {
        // 종료
        if (event.target.getCurrentTime() >= event.target.options.playerVars.end) {
          this.playCount += 1;
        }
        if (this.playCount >= 2) {
          this.visible = true;
        }
        if (this.playCount >= 3) {
          this.visible = false;
          this.playCount = 0;
          this.createPlayer("vJlVRlVyfrg", 10, 13);
          return;
        }
        this.ytPlayer.seekTo(event.target.options.playerVars.start);
        this.ytPlayer.playVideo();
      }
    },
    unMute() {
      this.isMuted = false;

      if (!this.ytPlayer) return;
      this.ytPlayer.unMute();
    },
    addUserInteractionListener() {
      document.addEventListener("click", this.handleUserInteraction);
      document.addEventListener("keydown", this.handleUserInteraction);
      document.addEventListener("touchstart", this.handleUserInteraction);
    },
    removeUserInteractionListener() {
      document.removeEventListener("click", this.handleUserInteraction);
      document.removeEventListener("keydown", this.handleUserInteraction);
      document.removeEventListener("touchstart", this.handleUserInteraction);
    },
    handleUserInteraction() {
      this.removeUserInteractionListener();
      this.unMute();
    },
  }
};
</script>

<style scoped>
.pip-card {
  width: 480px;
  height: 270px;
  position: fixed;
  top: 0;
  left: 0;
  padding: 0;
  cursor: grab;
  background: black;
  box-shadow: 4px 4px 10px rgba(0, 0, 0, 1);
  border-radius: 12px;
  z-index: 1000;
  transition: transform 0.1s ease-out; /* 부드러운 애니메이션 */
  visibility: hidden;
}

.pip-card.visible {
  visibility: visible;
}

.overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: transparent; /* 투명 */
  z-index: 10; /* iframe 위에 배치 */
}

.muted-overlay {
  display: flex;
  width: 100%;
  height: 100%;
  padding: 0;
  background-color: rgba(0, 0, 0, 0.8);
  justify-content: center;
  align-items: center;
}
</style>