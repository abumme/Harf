# Deleting your account and data — «Harf»

**Revision date:** 2026-09-08

This page explains how to delete your account and associated data in the «Harf» mobile application.

**Data operator:** Islomov Mekhrojbek, an individual, Republic of Uzbekistan. Email: lazydevscat@gmail.com.

## How to request deletion

**In the app (immediate):**
1. Open **Settings**.
2. Tap **Delete account**.
3. Confirm. Your server-side account and data are removed right away, and local progress on the device is reset.

**By email (if you can't access the app):**
Send a request from any address to **lazydevscat@gmail.com** with the subject "Delete my Harf account". Because accounts are anonymous by design, include any detail that helps us locate it (for example, the approximate date of first use, or the Google account you signed in with). We process such requests within 30 days.

## What is deleted

On deletion the following are removed from the server:

- the anonymous account identifier (random UUID);
- the linked sign-in identifier (Google/Apple provider + subject id), if you signed in;
- synchronized game statistics (language, puzzle number, win/attempts, update time);
- all session and refresh tokens.

Local data on your device (theme, language, unfinished round, local statistics) is cleared on the device as part of the same action.

## What is retained, and for how long

- We do not keep any personally identifying data after deletion — the app never stores your name, email, or profile photo on the server.
- Encrypted database backups may still contain your rows for up to **30 days**, after which they are overwritten and the data is permanently gone.
- Purchase records are held by Google Play / RevenueCat under their own policies; the app stores no payment details.

## Contact

Questions about deletion: **lazydevscat@gmail.com**.
