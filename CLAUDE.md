# Bolchi Keyboard — project notes for Claude

Android keyboard (IME) for Tara (Kolkata, Samsung phone with a wide/foldable screen). One keyboard for everything:
English typing, Bangla/Hindi script typing, and voice that writes Banglish/Hinglish.

Repo: github.com/banerjeetara-tech/bolchi-keyboard (branch `main`).

## How it ships
- Plain Java, no Kotlin, no AndroidX libraries; UI is built in code (no XML layouts).
- Every push to `main` makes GitHub Actions build an APK and publish a release `build-N` with `bolchi-keyboard.apk`.
  Push to `next` to test-compile without a release. Add `[skip ci]` to commits that change only docs.
- Signed with the fixed key `app/bolchi.keystore` (password `bolchi123`), so new builds install over old ones.
- Bump `versionCode`/`versionName` in `app/build.gradle` for each release. GitHub's build number runs ahead of the app version.
- No Android SDK in Claude's workspace: only GitHub can compile. Pure-Java parts (translit) can be tested locally with `javac`.

## Files (app/src/main/java/com/tara/bolchi/)
- `BolchiIME.java` — the keyboard: layouts, emoji panel, converter panel, voice, themes, problem report.
- `SetupActivity.java` — launcher app: mic permission, enable/switch keyboard, AI key box, problem report with Copy.
- `Translit.java` — Bengali script to Banglish (rules + word list). `HindiTranslit.java` — Hindi to Hinglish.
- `ScriptKeys.java` — Bangla/Hindi letter layouts; top row shows full vowels at word start, vowel signs after a consonant.
- `Phonetic.java` — type English, get Bangla/Hindi suggestions (inputtools.google.com, no key).
- `Converter.java` — converter options (see below). `Themes.java` — 27 themes. `Emojis.java` — emoji tabs.

## Current features (app version 9 / build-13)
- Bottom bar: palette (cycles themes), ⇄ converter, Bangla / Hindi / English chips, Speak.
  Tapping the already-selected Bangla/Hindi chip switches voice output to script (বাংলা / हिंदी) and back.
- Voice keeps listening through pauses until Stop (restarts Android SpeechRecognizer each round).
- Layout key next to the smiley cycles: EN → ab→বাং → ab→हिं → বাং keys → हिं keys.
- Number row, Samsung-style !#1 → 1/2, 2/2 symbol pages, emoji panel. No GIFs/stickers (Samsung has them).
- Keys type on touch-down with press highlight; touch area covers the gaps. Bigger lowercase letters, wide space bar.
- Long-press space opens the keyboard picker. `onEvaluateFullscreenMode` returns false.
- 27 themes: 10 bold (black combos, navy/coral, plum/peach, forest/cream/gold, teal/white/yellow) + 17 light.

## Open issue: converter "Translate to English" fails on Tara's phone
- Gemini (generativelanguage.googleapis.com) could not connect from her phone (connect hung > 30 s; report showed
  `SocketException: Socket closed` from our own 30 s cancel). The AI key itself was saved fine.
- inputtools.google.com DOES work from the keyboard (suggestions appear).
- Build-12 switched Translate to: Banglish → script via inputtools, then Google Translate `translate_a/single?client=gtx`
  (translate.google.com, fallback translate.googleapis.com), Gemini only as last fallback.
- Tara reports Translate still not working on build-13. Next step: get the Result-box message or the problem report,
  and check whether "To Bangla script" works (that uses only inputtools). If Translate hosts are blocked, consider
  translating through the inputtools host or another reachable endpoint.

## Working with Tara
- She uses a phone, writes briefly with typos; reply short and simple, no jargon.
- She wants a visual preview before design changes are pushed; small fixes can ship directly.
- She asked to keep token use low and dislikes long waits; say up front that a build takes about 4 minutes.
- Give the direct APK link after each release; install path: Chrome → My Files → Downloads → newest file → Update.
