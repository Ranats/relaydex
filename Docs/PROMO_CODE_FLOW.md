# Promo Code Flow

Use this flow for the paid Android app during Google Play closed testing.

## Goal

Keep `io.relaydex.android` as a paid app on Google Play, while giving one free promo code to each approved tester.

## Recommended tester flow

1. Join the Google Group
2. Opt in on the Play closed-test page
3. Request one promo code
4. Redeem the code in Google Play
5. Install the app
6. Keep the app installed and remain opted in for at least 14 days

## Recommended request channel

Best option:

- a Google Form dedicated to promo-code requests

Suggested fields:

- Google account email used for Play opt-in
- Google Group joined: yes or no
- Play opt-in completed: yes or no
- Android device model
- Optional notes

Why this is the best option:

- no need to DM people manually
- avoids posting promo codes publicly
- gives you a clean queue of requests

## Temporary fallback

If the Google Form is not ready yet, accept requests through a support email address.

Do not hand out promo codes in:

- public GitHub issues
- public X replies
- public Google Group posts

## Operator flow

1. Confirm the requester joined the Google Group
2. Confirm the requester says they completed Play opt-in
3. Assign one unused code
4. Send the code privately
5. Mark the assignment so the same code is not reused

## Local code assignment helper

Use:

```powershell
.\tools\assign-promo-code.ps1 `
  -CodeCsvPath "C:\Users\sopur\Downloads\promotion_codes.csv" `
  -RequesterId "tester@example.com" `
  -Notes "closed test wave 1"
```

The script:

- reads the Play promo-code CSV
- skips codes already recorded in `promotion_code_assignments.csv`
- appends one assignment record
- prints the assigned code

## Suggested public wording

Use wording like this:

```text
Google Play may show the paid price before redemption.
Closed testers can request one free promo code after joining the tester group and opting in on the Play test page.
```

## Important

- one tester should receive only one code
- do not publish raw codes on public pages
- keep the assignment log outside of public screenshots
