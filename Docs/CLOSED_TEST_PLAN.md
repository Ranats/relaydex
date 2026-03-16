# Closed Test Plan

This document is the practical rollout plan for getting Relaydex through Google Play closed testing with minimal manual coordination.

## What Google Play requires

For newly created personal developer accounts, Google Play requires:

- at least 12 testers
- testers must stay opted in for 14 continuous days
- this must happen in a closed test before production access can be requested

Important:

- Open testing does not replace this requirement.
- Internal testing does not replace this requirement.

## Closest thing to a TestFlight-style public link

The closest Play Store equivalent is:

1. Put testers into a Google Group
2. Publish a closed testing release
3. Share the Play opt-in link publicly

Google's official closed testing flow supports:

- adding testers by Google Groups
- a shareable opt-in link for the closed track

This means you do not need to DM every tester manually.

Current Relaydex recommendation:

- keep the current closed test free on Google Play
- mention that the planned production Android release is expected to become paid after closed testing ends

## Recommended setup

### 1. Create a Google Group for testers

Suggested group name:

```text
relaydex-android-testers@googlegroups.com
```

Recommended use:

- testers join the group themselves
- the group acts as the Play Console allowlist
- you avoid manual email-by-email management

### 2. Configure the closed track to use the Google Group

In Play Console:

- Testing > Closed testing
- Manage track
- Testers tab
- Add the Google Group address
- Add a feedback email or URL
- Save

Then copy the shareable opt-in link.

### 3. Create one public landing page

This page should contain:

- what Relaydex is
- that this is an Android closed test
- that users need a Google account on Android
- Step 1: join the tester group
- Step 2: open the Play opt-in link
- Step 3: install the test build
- Step 4: try one real pairing with a Windows host
- Step 5: keep the app installed and opted in for at least 14 days
- where to send feedback

Good places:

- GitHub README section
- GitHub Pages
- Notion public page
- simple static site

### 4. Use a form only as a helper, not as the source of truth

A Google Form is still useful, but only for:

- collecting device model
- collecting email for follow-up
- collecting bug reports
- confirming that users finished the opt-in process

The form does not make someone a valid Play tester by itself.
The tester still needs to:

- be in the Google Group, and
- click the Play opt-in link

## Best low-effort recruitment strategy

Do all of these in parallel:

- add a tester recruitment section to the GitHub README
- post the recruitment link on X
- post in Android / indie dev / Codex-related communities
- ask existing followers to join the 14-day closed test

Target:

- do not aim for exactly 12
- aim for 20 to 25 opt-ins

This gives you room for:

- people who forget to opt in
- people who uninstall
- people who never complete the Play flow

## What to ask testers to do

Keep the ask very simple:

1. Join the tester group
2. Open the Play opt-in link
3. Install the app
4. Open it once and try pairing with the host
5. Stay opted in for 14 days
6. Send feedback if something breaks

## Minimal success criteria

You should not apply for production access until:

- Play Console shows at least 12 opted-in testers
- those testers have stayed opted in for 14 continuous days
- you have a short summary of what feedback you received
- you can explain what you fixed during testing
