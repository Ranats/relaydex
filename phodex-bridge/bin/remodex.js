#!/usr/bin/env node
// FILE: remodex.js
// Purpose: CLI surface for bridge start, pairing reset, thread resume, and rollout tailing.
// Layer: CLI binary
// Exports: none
// Depends on: ../src

const {
  startBridge,
  resetBridgePairing,
  openLastActiveThread,
  watchThreadRollout,
} = require("../src");

const command = process.argv[2] || "up";

if (command === "up") {
  startBridge();
  return;
}

if (command === "reset-pairing") {
  try {
    resetBridgePairing();
    console.log("[relaydex] Cleared the saved pairing state. Run `relaydex up` to pair again.");
  } catch (error) {
    console.error(`[relaydex] ${(error && error.message) || "Failed to clear the saved pairing state."}`);
    process.exit(1);
  }
  return;
}

if (command === "resume") {
  try {
    const state = openLastActiveThread();
    console.log(
      `[relaydex] Opened last active thread: ${state.threadId} (${state.source || "unknown"})`
    );
  } catch (error) {
    console.error(`[relaydex] ${(error && error.message) || "Failed to reopen the last thread."}`);
    process.exit(1);
  }
  return;
}

if (command === "watch") {
  try {
    watchThreadRollout(process.argv[3] || "");
  } catch (error) {
    console.error(`[relaydex] ${(error && error.message) || "Failed to watch the thread rollout."}`);
    process.exit(1);
  }
  return;
}

if (command !== "up") {
  console.error(`Unknown command: ${command}`);
  console.error("Usage: relaydex up | relaydex reset-pairing | relaydex resume | relaydex watch [threadId]");
  process.exit(1);
}
