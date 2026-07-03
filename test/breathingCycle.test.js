import test from "node:test";
import assert from "node:assert/strict";
import { getBreathingPhase, getStarfieldState, STARFIELD_COLOR } from "../src/breathingCycle.js";

test("maps elapsed time to the 4-7-8 breathing phases", () => {
  assert.deepEqual(getBreathingPhase(0), { name: "contract", progress: 0 });
  assert.deepEqual(getBreathingPhase(2), { name: "contract", progress: 0.5 });
  assert.deepEqual(getBreathingPhase(4), { name: "hold", progress: 0 });
  assert.deepEqual(getBreathingPhase(10.5), { name: "hold", progress: 13 / 14 });
  assert.deepEqual(getBreathingPhase(11), { name: "expand", progress: 0 });
  assert.deepEqual(getBreathingPhase(15), { name: "expand", progress: 0.5 });
  assert.deepEqual(getBreathingPhase(19), { name: "contract", progress: 0 });
});

test("handles negative or repeated elapsed time safely", () => {
  assert.deepEqual(getBreathingPhase(-1), { name: "contract", progress: 0 });
  assert.deepEqual(getBreathingPhase(38), { name: "contract", progress: 0 });
});

test("maps breathing phases to minimal starfield gather states", () => {
  assert.deepEqual(getStarfieldState({ name: "idle", progress: 0 }), {
    scale: 1,
    corePull: 0,
    glow: 1,
  });
  assert.deepEqual(getStarfieldState({ name: "contract", progress: 0.5 }), {
    scale: 0.67,
    corePull: 0.39,
    glow: 1.34,
  });
  assert.deepEqual(getStarfieldState({ name: "hold", progress: 0.5 }), {
    scale: 0.34,
    corePull: 0.78,
    glow: 1.68,
  });
  assert.deepEqual(getStarfieldState({ name: "expand", progress: 0.5 }), {
    scale: 0.7,
    corePull: 0.39,
    glow: 1.46,
  });
});

test("keeps the starfield stable at the hold to exhale boundary", () => {
  assert.deepEqual(getStarfieldState({ name: "contract", progress: 1 }), {
    scale: 0.34,
    corePull: 0.78,
    glow: 1.68,
  });
  assert.deepEqual(
    getStarfieldState({ name: "hold", progress: 0.999 }),
    getStarfieldState({ name: "expand", progress: 0 })
  );
});

test("keeps the starfield particle palette uniformly white", () => {
  assert.deepEqual(STARFIELD_COLOR, {
    fill: "rgba(255, 255, 255, alpha)",
    shadow: "rgba(255, 255, 255, 0.46)",
  });
});
