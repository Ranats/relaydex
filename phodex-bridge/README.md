# relaydex

`relaydex` is the host-side CLI bridge for the Relaydex project.

It is intended to be published to npm as a separate package from the Android app. The bridge keeps Codex running locally on the host machine and lets a paired mobile client control it remotely.

## Install

```sh
npm install -g relaydex
```

## Usage

```sh
relaydex up
relaydex reset-pairing
relaydex resume
relaydex watch
```

## What it does

- starts local `codex app-server`
- connects to the relay session
- prints a pairing QR code and raw pairing payload
- forwards JSON-RPC traffic between the host and the mobile client
- handles git and workspace actions on the host machine

## Commands

### `relaydex up`

Starts the bridge, launches `codex app-server` locally, prints a pairing QR, and waits for the mobile client to connect.

### `relaydex reset-pairing`

Clears the saved trusted-device state so the next `relaydex up` starts a fresh pairing flow.

### `relaydex resume`

Reopens the last active thread in the local Codex desktop app if available.

### `relaydex watch [threadId]`

Tails the rollout log for the selected thread in real time.

## Environment variables

`relaydex` accepts both `RELAYDEX_*` and legacy `REMODEX_*` names.

Useful variables:

- `RELAYDEX_RELAY`: override the default relay URL
- `RELAYDEX_CODEX_ENDPOINT`: connect to an existing Codex WebSocket instead of spawning a local runtime
- `RELAYDEX_REFRESH_ENABLED`: enable the macOS desktop refresh workaround explicitly
- `RELAYDEX_REFRESH_DEBOUNCE_MS`: adjust refresh debounce timing

## Source builds

If you cloned the repository and want to run the bridge from source:

```sh
cd phodex-bridge
npm install
npm start
```

## Project status

This package is part of Relaydex, an independent fork of Remodex focused on the Windows host + Android client workflow.

It is not the official Remodex package.
