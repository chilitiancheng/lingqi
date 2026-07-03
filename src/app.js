import { getBreathingPhase } from "./breathingCycle.js";

const canvas = document.querySelector("#starfield");
const ctx = canvas.getContext("2d", { alpha: true });
const enterButton = document.querySelector("#enterButton");
const phaseLabel = document.querySelector("#phaseLabel");
const phaseHint = document.querySelector("#phaseHint");
const ring = document.querySelector("#breathRing");

const phaseCopy = {
  idle: ["浸入灵栖", "TOUCH TO ENTER LINGQI"],
  contract: ["吸气 4 秒", "星群收束"],
  hold: ["停留 7 秒", "让光停在身体里"],
  expand: ["呼气 8 秒", "星群散开"],
};

let running = false;
let startTime = 0;
let lastPhase = "idle";
let particles = [];
let audioContext = null;

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.floor(window.innerWidth * dpr);
  canvas.height = Math.floor(window.innerHeight * dpr);
  canvas.style.width = `${window.innerWidth}px`;
  canvas.style.height = `${window.innerHeight}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  buildParticles();
}

function buildParticles() {
  const count = Math.min(360, Math.max(180, Math.floor((window.innerWidth * window.innerHeight) / 3600)));
  particles = Array.from({ length: count }, (_, index) => {
    const angle = Math.random() * Math.PI * 2;
    const distance = Math.sqrt(Math.random());
    return {
      angle,
      distance,
      orbit: Math.random() * Math.PI * 2,
      size: 0.7 + Math.random() * 2.1,
      speed: 0.06 + Math.random() * 0.16,
      hue: index % 6 === 0 ? 44 : index % 4 === 0 ? 205 : 0,
      alpha: 0.38 + Math.random() * 0.62,
    };
  });
}

function ensureAudio() {
  if (!audioContext) {
    audioContext = new AudioContext();
  }
  if (audioContext.state === "suspended") {
    audioContext.resume();
  }
}

function playTone(type) {
  ensureAudio();
  const now = audioContext.currentTime;
  const osc = audioContext.createOscillator();
  const gain = audioContext.createGain();
  osc.type = type === "di" ? "sine" : "triangle";
  osc.frequency.setValueAtTime(type === "di" ? 1046 : 392, now);
  osc.frequency.exponentialRampToValueAtTime(type === "di" ? 784 : 262, now + 0.16);
  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(type === "di" ? 0.08 : 0.06, now + 0.018);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.22);
  osc.connect(gain).connect(audioContext.destination);
  osc.start(now);
  osc.stop(now + 0.24);
}

function setCopy(phaseName) {
  const [title, hint] = phaseCopy[phaseName] ?? phaseCopy.idle;
  phaseLabel.textContent = title;
  phaseHint.textContent = hint;
  document.body.dataset.phase = phaseName;
}

function phaseScale(phase) {
  if (!running) return 1;
  if (phase.name === "contract") return 1 - phase.progress * 0.42;
  if (phase.name === "hold") return 0.58;
  return 0.58 + phase.progress * 0.58;
}

function draw(time) {
  const width = window.innerWidth;
  const height = window.innerHeight;
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "rgba(0, 0, 0, 0.58)";
  ctx.fillRect(0, 0, width, height);

  const elapsed = running ? (time - startTime) / 1000 : 0;
  const phase = running ? getBreathingPhase(elapsed) : { name: "idle", progress: 0 };
  const scale = phaseScale(phase);
  const cx = width / 2;
  const cy = height * 0.56;
  const maxRadius = Math.min(width, height) * 0.54;
  const timeSeconds = time * 0.001;

  if (running && phase.name !== lastPhase) {
    if (phase.name === "contract") playTone("di");
    if (phase.name === "expand") playTone("ta");
    lastPhase = phase.name;
    setCopy(phase.name);
  }

  ring.style.setProperty("--breath-scale", scale.toFixed(3));

  for (const p of particles) {
    const drift = Math.sin(timeSeconds * p.speed + p.orbit) * 0.035;
    const radius = maxRadius * (0.22 + (p.distance + drift) * 0.78) * scale;
    const angle = p.angle + timeSeconds * p.speed * (0.34 + p.distance);
    const x = cx + Math.cos(angle) * radius;
    const y = cy + Math.sin(angle) * radius * 0.62;
    const glow = p.size * (running && phase.name === "hold" ? 1.35 : 1);
    ctx.beginPath();
    ctx.fillStyle = `hsla(${p.hue}, ${p.hue === 0 ? 0 : 80}%, ${p.hue === 44 ? 72 : 92}%, ${p.alpha})`;
    ctx.shadowColor = p.hue === 44 ? "rgba(255, 226, 142, 0.42)" : "rgba(255, 255, 255, 0.46)";
    ctx.shadowBlur = 11;
    ctx.arc(x, y, glow, 0, Math.PI * 2);
    ctx.fill();
  }

  requestAnimationFrame(draw);
}

function startBreathing() {
  ensureAudio();
  running = true;
  startTime = performance.now();
  lastPhase = "";
  enterButton.classList.add("is-running");
  enterButton.setAttribute("aria-pressed", "true");
}

enterButton.addEventListener("click", startBreathing);
window.addEventListener("resize", resize);

resize();
setCopy("idle");
requestAnimationFrame(draw);
