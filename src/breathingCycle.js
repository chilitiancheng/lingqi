export const BREATHING_PHASES = [
  { name: "contract", duration: 4 },
  { name: "hold", duration: 7 },
  { name: "expand", duration: 8 },
];

export const BREATHING_CYCLE_SECONDS = BREATHING_PHASES.reduce((sum, phase) => sum + phase.duration, 0);

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
      scale: round(1 - gather * 0.78),
      corePull: round(gather),
      glow: round(1 + gather * 0.95),
    };
  }

  if (phase?.name === "hold") {
    const pulse = Math.sin(progress * Math.PI * 2);
    return {
      scale: round(0.24 + pulse * 0.018),
      corePull: 1,
      glow: round(1.46 + Math.abs(pulse) * 0.06),
    };
  }

  if (phase?.name === "expand") {
    const release = easeInOutSine(progress);
    return {
      scale: round(0.24 + release * 0.82),
      corePull: round(1 - release),
      glow: round(1.46 - release * 0.42),
    };
  }

  return {
    scale: 1,
    corePull: 0,
    glow: 1,
  };
}
