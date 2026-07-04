import { getBreathingPhase, getStarfieldState } from "./breathingCycle.js";

const canvas = document.querySelector("#starfield");
const enterButton = document.querySelector("#enterButton");
const phaseLabel = document.querySelector("#phaseLabel");
const phaseHint = document.querySelector("#phaseHint");

const phaseCopy = {
  idle: ["浸入灵栖", "TOUCH TO ENTER LINGQI"],
  contract: ["吸气 4 秒", "星群收束"],
  hold: ["停留 7 秒", "让光停在身体里"],
  expand: ["呼气 8 秒", "星群散开"],
};

const PARTICLE_COUNT = 200;
const PARTICLE_SPREAD = 10;
const PARTICLE_BASE_SIZE = 58;
const SIZE_RANDOMNESS = 0.12;
const CAMERA_DISTANCE = 26;
const FOV = 20;

let running = false;
let startTime = 0;
let lastPhase = "idle";
let audioContext = null;
let gl = null;
let program = null;
let buffers = null;
let particleState = null;
let previousFrameTime = 0;
let particleTime = 0;

const vertexShaderSource = `
  attribute vec3 position;
  attribute vec4 random;

  uniform float uTime;
  uniform float uSpread;
  uniform float uBaseSize;
  uniform float uAspect;
  uniform float uFovScale;
  uniform float uCameraDistance;
  uniform float uVerticalOffset;

  varying vec4 vRandom;

  void main() {
    vRandom = random;

    vec3 pos = position * uSpread;
    pos.z *= 10.0;

    float t = uTime;
    pos.x += sin(t * random.z + 6.28 * random.w) * mix(0.1, 1.5, random.x);
    pos.y += sin(t * random.y + 6.28 * random.x) * mix(0.1, 1.5, random.w);
    pos.z += sin(t * random.w + 6.28 * random.y) * mix(0.1, 1.5, random.z);

    float viewZ = uCameraDistance - pos.z;
    vec2 projected = pos.xy / max(viewZ, 0.01) * uFovScale;

    gl_Position = vec4(projected.x / uAspect, projected.y + uVerticalOffset, 0.0, 1.0);
  gl_PointSize = (uBaseSize * (1.0 + ${SIZE_RANDOMNESS.toFixed(2)} * (random.x - 0.5))) / max(viewZ, 0.01);
  }
`;

const fragmentShaderSource = `
  precision highp float;

  varying vec4 vRandom;

  void main() {
    vec2 uv = gl_PointCoord.xy;
    float d = length(uv - vec2(0.5));

    if (d > 0.5) {
      discard;
    }

    float shimmer = 0.08 * sin(uv.y + vRandom.y * 6.28);
    gl_FragColor = vec4(vec3(1.0 + shimmer), 1.0);
  }
`;

function createShader(type, source) {
  const shader = gl.createShader(type);
  gl.shaderSource(shader, source);
  gl.compileShader(shader);

  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    throw new Error(gl.getShaderInfoLog(shader) || "Shader compile failed");
  }

  return shader;
}

function createProgram() {
  const vertexShader = createShader(gl.VERTEX_SHADER, vertexShaderSource);
  const fragmentShader = createShader(gl.FRAGMENT_SHADER, fragmentShaderSource);
  const shaderProgram = gl.createProgram();
  gl.attachShader(shaderProgram, vertexShader);
  gl.attachShader(shaderProgram, fragmentShader);
  gl.linkProgram(shaderProgram);

  if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
    throw new Error(gl.getProgramInfoLog(shaderProgram) || "Program link failed");
  }

  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);
  return shaderProgram;
}

function buildParticles() {
  const positions = new Float32Array(PARTICLE_COUNT * 3);
  const randoms = new Float32Array(PARTICLE_COUNT * 4);

  for (let i = 0; i < PARTICLE_COUNT; i += 1) {
    let x = 0;
    let y = 0;
    let z = 0;
    let len = 0;

    do {
      x = Math.random() * 2 - 1;
      y = Math.random() * 2 - 1;
      z = Math.random() * 2 - 1;
      len = x * x + y * y + z * z;
    } while (len > 1 || len === 0);

    const radius = Math.cbrt(Math.random());
    positions.set([x * radius, y * radius, z * radius], i * 3);
    randoms.set([Math.random(), Math.random(), Math.random(), Math.random()], i * 4);
  }

  return { positions, randoms };
}

function createBuffer(data, size, attributeName) {
  const buffer = gl.createBuffer();
  const location = gl.getAttribLocation(program, attributeName);
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(location);
  gl.vertexAttribPointer(location, size, gl.FLOAT, false, 0, 0);
  return buffer;
}

function initParticles() {
  gl = canvas.getContext("webgl", {
    alpha: true,
    antialias: true,
    depth: false,
    premultipliedAlpha: false,
  });

  if (!gl) return;

  program = createProgram();
  gl.useProgram(program);
  particleState = buildParticles();
  buffers = {
    position: createBuffer(particleState.positions, 3, "position"),
    random: createBuffer(particleState.randoms, 4, "random"),
  };

  gl.clearColor(0, 0, 0, 0);
  gl.disable(gl.DEPTH_TEST);
  gl.enable(gl.BLEND);
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
}

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const width = Math.floor(window.innerWidth * dpr);
  const height = Math.floor(window.innerHeight * dpr);
  canvas.width = width;
  canvas.height = height;
  canvas.style.width = `${window.innerWidth}px`;
  canvas.style.height = `${window.innerHeight}px`;

  if (gl) {
    gl.viewport(0, 0, width, height);
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

function getParticleRenderState(phase, starfield) {
  if (phase.name === "contract") {
    return {
      spread: PARTICLE_SPREAD * (1 - starfield.corePull * 0.62),
      size: PARTICLE_BASE_SIZE * (1 + starfield.corePull * 0.32),
      speed: 0.1,
      rotate: true,
    };
  }

  if (phase.name === "hold") {
    return {
      spread: PARTICLE_SPREAD * 0.52,
      size: PARTICLE_BASE_SIZE * 1.32,
      speed: 0,
      rotate: false,
    };
  }

  if (phase.name === "expand") {
    const release = Math.sin((Math.PI * phase.progress) / 2);
    return {
      spread: PARTICLE_SPREAD * (0.52 + release * 0.28),
      size: PARTICLE_BASE_SIZE * 1.32,
      speed: 0,
      rotate: false,
    };
  }

  return {
    spread: PARTICLE_SPREAD,
    size: PARTICLE_BASE_SIZE,
    speed: 0.1,
    rotate: true,
  };
}

function draw(time) {
  if (!gl) return;

  const elapsed = running ? (time - startTime) / 1000 : 0;
  const phase = running ? getBreathingPhase(elapsed) : { name: "idle", progress: 0 };
  const starfield = getStarfieldState(phase);
  const renderState = getParticleRenderState(phase, starfield);
  const delta = previousFrameTime ? time - previousFrameTime : 0;
  previousFrameTime = time;

  if (running && phase.name !== lastPhase) {
    if (phase.name === "contract") playTone("di");
    if (phase.name === "expand") playTone("ta");
    lastPhase = phase.name;
    setCopy(phase.name);
  }

  if (renderState.speed > 0) {
    particleTime += delta * renderState.speed * 0.001;
  }

  gl.clear(gl.COLOR_BUFFER_BIT);
  gl.useProgram(program);
  gl.uniform1f(gl.getUniformLocation(program, "uTime"), particleTime);
  gl.uniform1f(gl.getUniformLocation(program, "uSpread"), renderState.spread);
  gl.uniform1f(gl.getUniformLocation(program, "uBaseSize"), renderState.size * Math.min(window.devicePixelRatio || 1, 2));
  gl.uniform1f(gl.getUniformLocation(program, "uAspect"), canvas.width / canvas.height);
  gl.uniform1f(gl.getUniformLocation(program, "uFovScale"), 1 / Math.tan((FOV * Math.PI) / 360));
  gl.uniform1f(gl.getUniformLocation(program, "uCameraDistance"), CAMERA_DISTANCE);
  gl.uniform1f(gl.getUniformLocation(program, "uVerticalOffset"), window.innerWidth < 560 ? -0.42 : -0.28);
  gl.drawArrays(gl.POINTS, 0, PARTICLE_COUNT);

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

initParticles();
resize();
setCopy("idle");
requestAnimationFrame(draw);
