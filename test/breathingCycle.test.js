import test from "node:test";
import assert from "node:assert/strict";
import { getBreathingPhase, getParticleAngle, getStarfieldState, STARFIELD_COLOR } from "../src/breathingCycle.js";

function assertNear(actual, expected, epsilon = 0.0000001) {
  assert.ok(Math.abs(actual - expected) <= epsilon, `${actual} should be within ${epsilon} of ${expected}`);
}

function assertStateNear(actual, expected) {
  assertNear(actual.scale, expected.scale);
  assertNear(actual.corePull, expected.corePull);
  assertNear(actual.glow, expected.glow);
}

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
  assertStateNear(getStarfieldState({ name: "contract", progress: 0.5 }), {
    scale: 0.67,
    corePull: 0.39,
    glow: 1.34,
  });
  assert.deepEqual(getStarfieldState({ name: "hold", progress: 0.5 }), {
    scale: 0.34,
    corePull: 0.78,
    glow: 1.68,
  });
  assertStateNear(getStarfieldState({ name: "expand", progress: 0.5 }), {
    scale: 0.67,
    corePull: 0.39,
    glow: 1.34,
  });
});

test("keeps exhale animation values continuous between adjacent frames", () => {
  const frameTime = 1 / 60;
  const exhaleDuration = 8;

  for (let second = 0; second < exhaleDuration; second += frameTime) {
    const current = getStarfieldState({ name: "expand", progress: second / exhaleDuration });
    const next = getStarfieldState({ name: "expand", progress: (second + frameTime) / exhaleDuration });

    assert.ok(Math.abs(next.scale - current.scale) < 0.0024);
    assert.ok(Math.abs(next.corePull - current.corePull) < 0.0026);
    assert.ok(Math.abs(next.glow - current.glow) < 0.0023);
  }
});

test("keeps the starfield stable at the hold to exhale boundary", () => {
  assertStateNear(getStarfieldState({ name: "contract", progress: 1 }), {
    scale: 0.34,
    corePull: 0.78,
    glow: 1.68,
  });
  assertStateNear(getStarfieldState({ name: "expand", progress: 0 }), getStarfieldState({ name: "hold", progress: 0.999 }));
});

test("returns exhale to the next inhale starting state without a cycle-end snap", () => {
  assertStateNear(getStarfieldState({ name: "expand", progress: 1 }), getStarfieldState({ name: "contract", progress: 0 }));
});

test("keeps the starfield particle palette uniformly white", () => {
  assert.deepEqual(STARFIELD_COLOR, {
    fill: "rgba(255, 255, 255, alpha)",
    shadow: "rgba(255, 255, 255, 0.46)",
  });
});

test("keeps particle orbit angle independent from breathing state", () => {
  assert.equal(
    getParticleAngle({ baseAngle: 1, timeSeconds: 12, speed: 0.1, distance: 0.5 }),
    getParticleAngle({ baseAngle: 1, timeSeconds: 12, speed: 0.1, distance: 0.5 })
  );
  assert.equal(getParticleAngle({ baseAngle: 1, timeSeconds: 0, speed: 0.1, distance: 0.5 }), 1);
  assert.equal(getParticleAngle({ baseAngle: 1, timeSeconds: 12, speed: 0.1, distance: 0.5 }), 1);
});
