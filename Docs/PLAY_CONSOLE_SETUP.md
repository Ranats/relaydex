# Play Console Setup

This document maps the required Play Console setup items to the current Relaydex app.

## 1. Privacy policy

Google Play requires a public privacy policy URL for apps on Play.

Use:

- a GitHub Pages URL, or
- a public static page, or
- a public page in your own site

Recommended:

- publish [`Docs/PRIVACY_POLICY.md`](./PRIVACY_POLICY.md) as a public web page

## 2. App access

Recommended answer for Relaydex:

- `All functionality is available without special access`

Why:

- the app does not require a login account
- the app does not hide functionality behind credentials
- it does require the user to run their own local host bridge, but that is not the same as protected app access

If Google asks for clarification, describe it like this:

```text
This app is a remote control client for a local bridge running on the user's own computer. The Android app itself does not require an account login or special credentials.
```

## 3. Ads

Recommended answer:

- `No, my app does not contain ads`

## 4. Content rating

Recommended path:

- complete the questionnaire
- choose the non-violent productivity / developer tools style answers

Expected outcome:

- a general or low rating

## 5. Target audience

Recommended answer:

- not designed for children
- primary audience: adults

Suggested selection:

- `18 and over`

## 6. Data safety

Use conservative answers.

Relaydex likely needs to disclose at least:

- user-provided content
  because prompts and conversation content are sent through the app
- app activity
  because conversations and thread usage are part of core functionality
- device or other identifiers
  because the pairing flow uses device IDs / session IDs / cryptographic identity values

For each item, the exact Play answers depend on how narrowly Google defines collection and sharing in your form. Since the relay and local bridge are part of the product flow, do not answer as if the app never transmits anything.

Recommended safe framing:

- data is used for app functionality
- data is encrypted in transit after secure pairing
- data is not sold
- data is not used for advertising

Before submitting Data safety, review:

- what the relay can see
- what the app stores locally
- whether crash / analytics SDKs are added later

## 7. Government apps

Recommended answer:

- `No`

## 8. Financial features

Recommended answer:

- `No, the app does not provide financial features`

## 9. Health

Recommended answer:

- `No`

## 10. App category and contact details

Recommended values:

- Category: `Productivity` or `Tools`
- Contact email: your support email
- Website: optional, but GitHub repo or landing page is better than blank
- Closed-test feedback URL: `https://github.com/Ranats/relaydex/issues/new?template=closed-test-feedback.yml`
- Closed-test feedback email: `saxophonia991@gmail.com`

## 11. Store listing

You need these before rollout:

- app name
- short description
- full description
- app icon
- feature graphic if required by your locale/setup
- screenshots

You can base the text on:

- [`Docs/PLAY_STORE_COPY.md`](./PLAY_STORE_COPY.md)

## 12. Countries / regions

Your error shows the release track has no country or region selected.

Fix:

- go to the closed testing track
- open `Countries/regions`
- select at least one country
- for tester recruitment, selecting broad coverage is usually easier

If you recruit internationally, choose a broad country list so testers do not hit `not available in your country`.
