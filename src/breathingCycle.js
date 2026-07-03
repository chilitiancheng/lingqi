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
