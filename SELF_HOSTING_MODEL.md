# Public Repo and Self-Hosting

This file explains what the public Relaydex repository is for, what it includes, and what it does not include.

If you cloned Relaydex from GitHub, the intended path is local-first usage or self-hosting on infrastructure you control.

## What the Public Repo Includes

The public repository includes:

- the Windows-friendly host bridge
- the Android app source code
- the public relay code
- local pairing and self-hosting documentation

The public repository is meant to be usable without private hosted dependencies baked into the source tree.

## What the Public Repo Does Not Include

The public repository does not include:

- private production relay URLs
- private Google Play defaults
- private npm publish-time defaults
- private notification credentials
- private deployment secrets

If you are running from source, assume you should provide your own relay setup or use the defaults exposed by the public bridge package.

## The Self-Hosting Path

If you use the public repo, expect one of these flows:

1. local LAN pairing on your own machine
2. a self-hosted relay on your own VPS, passed in through `RELAYDEX_RELAY`

That means:

- Codex still runs on your Windows host
- git commands still run on your Windows host
- the Android phone is still a paired remote client
- the relay is only the transport layer

For the full setup guide, read [Docs/self-hosting.md](Docs/self-hosting.md).

## Why the Repo Stays Generic

The public repo stays generic on purpose.

That keeps the self-host path honest:

- people can inspect the transport and pairing code
- people can run Relaydex locally
- people can self-host their own relay
- people are not silently tied to someone else's hosted infrastructure

## What to Keep Private

If you fork or self-host Relaydex, keep these things out of the public repo:

- your deployed hostname
- your VPS IP addresses
- any APNs or notification credentials
- any private build overrides
- any publish-time package defaults

Those belong in your own environment, private config, or release pipeline.
