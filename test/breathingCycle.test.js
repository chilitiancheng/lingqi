import test from "node:test";
import assert from "node:assert/strict";
import { getBreathingPhase } from "../src/breathingCycle.js";

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
