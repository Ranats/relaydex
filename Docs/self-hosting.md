# Self-Hosting Relaydex

This guide is for developers who clone the public GitHub repository and want to run Relaydex on infrastructure they control.

It covers two supported setups:

1. local LAN pairing on your own machine
2. a self-hosted VPS relay that your bridge connects to over the internet

This document intentionally avoids private hosted-service details. If you are using the public repo, assume you are bringing your own relay endpoint when you want full control.

## What Relaydex Self-Hosting Means

Relaydex is local-first.

That means:

- the bridge runs on your own Windows machine
- Codex runs on your own Windows machine
- git commands run on your own Windows machine
- your Android device is a remote control
- the relay is only a transport layer for pairing and encrypted message forwarding

The relay does not run Codex and does not get plaintext application payloads after the secure handshake completes.

## Option 1: Local LAN Setup

This is the easiest way to try the public repo.

### What you need

- a Windows machine with Codex CLI installed
- an Android device with a Relaydex build installed
- both devices on the same reachable network

### Start the bridge

On the Windows host:

```sh
npm install -g relaydex
relaydex up
```

Then:

1. Open the Android app
2. Scan the QR code from inside the app
3. Start a thread and send a message

## Option 2: Self-Hosted VPS Relay

Use this when you want the bridge on your Windows host to connect through a relay you run on a VPS.

### What runs where

On your VPS:

- the Relaydex relay

On your Windows host:

- the Relaydex bridge
- Codex CLI / `codex app-server`

On your Android device:

- the Relaydex app

### Start the relay on the VPS

From the public repo:

```sh
cd relay
npm install
npm start
```

By default the relay listens on port `9000`.

### Verify the relay

On the VPS:

```sh
curl http://127.0.0.1:9000/health
```

### Point the bridge at your relay

On the Windows host:

```sh
RELAYDEX_RELAY="wss://relay.example.com/relay" relaydex up
```

The bridge will print a QR code. That QR carries the relay URL and session information, so the Android app does not need a hardcoded relay endpoint in the public source build.

## Reverse Proxy Notes

If your relay sits behind Traefik, Nginx, or Caddy:

- forward WebSocket upgrades correctly
- forward the `/relay/...` path to the relay process
- only enable trust-proxy behavior if you are sure the proxy is trusted

## What Not to Commit

If you are self-hosting from the public repo, keep these things out of Git:

- your real relay hostname
- your VPS IP addresses
- any notification credentials
- any private package defaults

The public repo should stay generic. Your actual deployment values belong in your own environment, build pipeline, or private config.

## Troubleshooting

### The bridge starts but Android cannot connect

Check:

- the relay is reachable from the phone
- your reverse proxy forwards WebSockets
- the bridge is using the correct `RELAYDEX_RELAY`
- the public endpoint uses `wss://` if you are going over the internet

### The relay health check works, but pairing still fails

That usually means one of these:

- the public path is wrong
- the reverse proxy is not forwarding upgrades
- the bridge is pointing at the wrong relay base URL
