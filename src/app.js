import { getBreathingPhase, getParticleAngle, getStarfieldState, STARFIELD_COLOR } from "./breathingCycle.js";

const canvas = document.querySelector("#starfield");
const ctx = canvas.getContext("2d", { alpha: true });
const enterButton = document.querySelector("#enterButton");
const phaseLabel = document.querySelector("#phaseLabel");
const phaseHint = document.querySelector("#phaseHint");

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
  const count = Math.min(340, Math.max(180, Math.floor((window.innerWidth * window.innerHeight) / 3700)));
  const pairCount = Math.floor(count / 2);
  particles = [];
  const goldenAngle = Math.PI * (3 - Math.sqrt(5));

  for (let index = 0; index < pairCount; index += 1) {
    const sequence = (index + 0.5) / pairCount;
    const angle = index * goldenAngle;
    const distance = Math.sqrt(sequence);
    const particle = {
      angle,
      distance,
      orbit: angle,
      size: 0.42,
      speed: 0.06 + Math.random() * 0.16,
      alpha: 0.52,
      coreBias: sequence,
    };

    particles.push(particle, {
      ...particle,
      angle: angle + Math.PI,
    });
  }
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

function draw(time) {
  const width = window.innerWidth;
  const height = window.innerHeight;
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#000000";
  ctx.fillRect(0, 0, width, height);

  const elapsed = running ? (time - startTime) / 1000 : 0;
  const phase = running ? getBreathingPhase(elapsed) : { name: "idle", progress: 0 };
  const starfield = getStarfieldState(phase);
  const cx = width / 2;
  const cy = height * 0.6;
  const maxRadius = Math.min(width, height) * 0.66;
  const timeSeconds = time * 0.001;

  if (running && phase.name !== lastPhase) {
    if (phase.name === "contract") playTone("di");
    if (phase.name === "expand") playTone("ta");
    lastPhase = phase.name;
    setCopy(phase.name);
  }

  for (const p of particles) {
    const drift = 0;
    const looseRadius = maxRadius * (0.14 + (p.distance + drift) * 0.86) * starfield.scale;
    const coreRadius = maxRadius * (0.03 + p.coreBias * 0.18) * 0.72;
    const radius = looseRadius * (1 - starfield.corePull) + coreRadius * starfield.corePull;
    const angle = getParticleAngle({ baseAngle: p.angle, timeSeconds, speed: p.speed, distance: p.distance });
    const renderCorePull = phase.name === "expand" ? 0.78 : starfield.corePull;
    const renderGlow = phase.name === "expand" ? 1.68 : starfield.glow;
    const x = cx + Math.cos(angle) * radius;
    const y = cy + Math.sin(angle) * radius * (0.64 - renderCorePull * 0.24);
    const glow = p.size * renderGlow * (1 + renderCorePull * (1 - p.distance) * 0.48);
    const softRadius = glow * 4.8;
    const gradient = ctx.createRadialGradient(x, y, 0, x, y, softRadius);
    gradient.addColorStop(0, `rgba(255, 255, 255, ${p.alpha})`);
    gradient.addColorStop(0.36, `rgba(255, 255, 255, ${p.alpha * 0.22})`);
    gradient.addColorStop(1, "rgba(255, 255, 255, 0)");
    ctx.beginPath();
    ctx.fillStyle = gradient;
    ctx.shadowColor = STARFIELD_COLOR.shadow;
    ctx.shadowBlur = 8 + renderCorePull * 16;
    ctx.arc(x, y, softRadius, 0, Math.PI * 2);
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
