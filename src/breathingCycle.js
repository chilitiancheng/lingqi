export const BREATHING_PHASES = [
  { name: "contract", duration: 4 },
  { name: "hold", duration: 7 },
  { name: "expand", duration: 8 },
];

export const BREATHING_CYCLE_SECONDS = BREATHING_PHASES.reduce((sum, phase) => sum + phase.duration, 0);
export const STARFIELD_COLOR = {
  fill: "rgba(255, 255, 255, alpha)",
  shadow: "rgba(255, 255, 255, 0.46)",
};

export function getBreathingPhase(elapsedSeconds) {
  if (!Number.isFinite(elapsedSeconds) || elapsedSeconds <= 0) {
    return { name: "contract", progress: 0 };
  }

  let cursor = elapsedSeconds % BREATHING_CYCLE_SECONDS;
  for (const phase of BREATHING_PHASES) {
    if (cursor < phase.duration) {
      return {
        name: phase.name,
        progress: cursor / phase.duration,
      };
    }
    cursor -= phase.duration;
  }

  return { name: "contract", progress: 0 };
}

function round(value) {
  return Math.round(value * 100) / 100;
}

function easeOutCubic(value) {
  return 1 - (1 - value) ** 3;
}

function easeInOutSine(value) {
  return 0.5 - Math.cos(Math.PI * value) / 2;
}

export function getStarfieldState(phase) {
  const progress = Math.max(0, Math.min(1, phase?.progress ?? 0));

  if (phase?.name === "contract") {
    const gather = easeOutCubic(progress);
    return {
      scale: round(1 - gather * 0.66),
      corePull: round(gather * 0.82),
      glow: round(1 + gather * 1.05),
    };
  }

  if (phase?.name === "hold") {
    const pulse = Math.sin(progress * Math.PI * 2);
    return {
      scale: round(0.34 + pulse * 0.025),
      corePull: round(0.78 + Math.abs(pulse) * 0.04),
      glow: round(1.68 + Math.abs(pulse) * 0.1),
    };
  }

  if (phase?.name === "expand") {
    const release = easeInOutSine(progress);
    return {
      scale: round(0.34 + release * 0.72),
      corePull: round((1 - release) * 0.78),
      glow: round(1.68 - release * 0.44),
    };
  }

  return {
    scale: 1,
    corePull: 0,
    glow: 1,
  };
}
