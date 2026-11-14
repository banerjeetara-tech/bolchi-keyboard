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
- `Words.java` — English suggestions + auto-correct (word list in `app/src/main/assets/words.txt`, Banglish/Hinglish words protected).
  `Spoken.java` — voice self-correction ("five no sorry six" → "six", "scratch that").
  `Swipe.java` — gesture typing decoder.
- `Converter.java` — converter options (see below). `Themes.java` — 27 themes. `Emojis.java` — emoji tabs.

## Current features (app version 9 / build-13)
- Bottom bar: palette (cycles themes), ⇄ converter, Bangla / Hindi / English chips, Speak.
  Tapping the already-selected Bangla/Hindi chip switches voice output to script (বাংলা / हिंदी) and back.
- Voice keeps listening through pauses until Stop (restarts Android SpeechRecognizer each round).
- Layout key next to the smiley cycles: EN → ab→বাং → ab→हिं → বাং keys → हिं keys.
- Number row, Samsung-style !#1 → 1/2, 2/2 symbol pages, emoji panel. GIFs: see version 43 below (she first said Samsung only, then asked for GIFs beside the emoji suggestions).
- Keys type on touch-down with press highlight; touch area covers the gaps. Bigger lowercase letters, wide space bar.
- Long-press space opens the keyboard picker. `onEvaluateFullscreenMode` returns false.
- 27 themes: 10 bold (black combos, navy/coral, plum/peach, forest/cream/gold, teal/white/yellow) + 17 light.

## Added in app version 10 (not yet confirmed by Tara)
- Letter size 22–26 dp (capitals 0.9×), regular weight, ignores system font scale; keys 50 dp tall. Tara rejected 38 dp bold
  and 33 dp as "too large, odd": keep letters about the size of the number row. Rows use `setBaselineAligned(false)` so keys stay level.
- English keys: suggestion strip (3 words + Auto ✓/✕ toggle). Space/punctuation auto-corrects typos;
  backspace right after puts the typed word back and remembers it (prefs `ownWords`).
- No paste button or paste suggestion: Tara wants only the app's normal long-press Paste (`paste()` in BolchiIME is unused).
- Key press only recolours the key (no re-layout). Phonetic suggestions wait 140 ms after the last key (one request per pause).
  Tara still said "typing is not smooth" on version 10 while on the ab→বাং layout: ask which layout and what exactly lags.
- Touch (version 12): `KeyArea` (the keys container) handles every finger itself and sends it to the nearest key
  (`keyAt`, within 34 dp), because normal Android delivery dropped the second thumb when the first finger rested on an empty
  spot and ignored taps between keys. Emoji and converter pages still use normal touch. Keys type first, then buzz.
  Tara reported on version 10/11: letters come late, taps missed, wrong neighbour letter (English too) — not yet confirmed fixed.
- Gesture typing (version 14, EN keys only): slide over a word's letters and lift. `Swipe.java` matches the path against the
  word list (top-1 about 92% on synthetic paths, untested on a real phone). The letter typed at touch-down is replaced by the word
  plus a space; other guesses show in the suggestion row; backspace right after removes the whole word. Trail drawn in `KeyArea`.
- Voice (version 16): build-19 (another session) forced Google's voice engine and a 1.5 s silence wait; after that Tara said Bengali voice got worse after version 10–14 ("was better yesterday"). So voice is back to
  build-13 behaviour by default (phone's own engine, 5 s / 4 s silence, 15 s minimum): Bangla/Hindi speech is written exactly as heard. `Spoken.java` rules run only in English mode.
  AI tidy (Gemini `Converter.tidy`, cleans everything spoken so far after each pause) is OFF by default; holding the Speak
  button turns it on/off (pref `aiTidy`). Do not turn voice changes on by default again without her testing them.
- My words: box 6 in the Bolchi app (pref `myWords`, one entry per line, e.g. her email). Typing the first 3+ letters on the EN keys
  shows the entry in the right suggestion slot. Never put her email or other personal text in the repo.
- Learning: an email typed once on the keys is saved to `myWords` automatically (`tokenEnd`); a new word not in the dictionary is
  saved to `ownWords` and offered first next time. Email/web boxes show the strip with only her saved entries; a dot inside an
  email gets no space. Password boxes show no strip.
- Voice beeps: `hushBeep()` mutes music/notification/system streams for 0.9 s around every recogniser restart and stop, so only
  the first start beeps (Tara: "sounds constantly till I press stop"). Not confirmed on her phone.
- Tara's main ask: speak Bengali mixed with English words. The engine listens only in the dark chip's language. Her screenshots
  showed the English chip dark; she also reported the app shows "Bolchi 7.0" (so check which build is really installed).
  Idea not yet tried: language-switch extras (API 34 `ENABLE_LANGUAGE_SWITCH`) for mixed speech — only with her testing.
- MAIN ASK (clarified, version 20): she speaks Bengali mixed with English on the Bangla chip and wants Banglish for the Bengali
  and correct English spelling for the English. The bn-IN engine writes English by sound ("aai em apdeting phrom the link").
  Only the AI can repair that, so the AI fix (`Converter.tidy`, pref `aiFix`) is ON by default when an AI key is saved; holding
  Speak turns it off. If Gemini does not connect from her phone the text stays as spoken (space bar says so) — then a local
  phonetic respelling table (CMUdict-based) is the fallback idea. On the English chip the phone engine mishears words
  ("app version and GitHub version" → "abortion and guitar version"); that is engine quality, not Bolchi.
- The "Bolchi 7.0" she saw was an OLD saved problem report in the app, not the installed version. The app now shows
  "Installed version" and has a **Test AI key** button (`Converter.test`) that prints Google's exact answer.
- Her AI key starts with `AQ.` (new AI Studio "Auth key" format, not `AIza`). Forum reports say many AQ. keys get
  401 ACCESS_TOKEN_TYPE_UNSUPPORTED on generativelanguage.googleapis.com. Get her Test AI key result before more AI work.
  Never write her key in the repo or in chat.
- CONFIRMED on her phone (build-25, app 21.0): Test AI key says "AI works" — Gemini connects and her AQ. key is accepted.
  The AI fix only runs when a voice round ends, i.e. after about 5 s of silence; if she finds that slow, shorten the silence wait.
- Enter key label follows the box (`paintEnter`): Go / Search / Send / Next / Done, else ⏎ (Tara asked, like Samsung).
  Tara rejected a "Go → Search by hint" heuristic (version 29): keep labels exactly as the app asks (version 22 behaviour).
- Version 23: stammer handling ("wh what's" → "What's"): AI prompt covers false starts/fillers; `Spoken.fix` drops a 1–3 letter
  broken start locally (English chip only). Silence wait shortened to 2.5 s / 2 s so the AI fix arrives sooner. Untested on phone.
- OTP (version 24): Android 11+ inline suggestions. `onCreateInlineSuggestionsRequest` / `onInlineSuggestionsResponse` show the
  one-time code above the keys in `otpRow`; tapping it fills the box via Android. Tara's phone is Android 17. Compile-checked
  against Robolectric android-all 14 jar (real API 34 classes), not run on a phone. Needs Android's SMS/autofill to supply the code.
- SMS codes (version 25): `SmsCodeReceiver` (RECEIVE_SMS, asked on the setup screen) keeps only a 4–8 digit code from a message
  that mentions code/OTP/verification, in prefs `smsCode`/`smsCodeAt`. The keyboard shows "Code 123456 (tap to fill)" above the keys
  for 2 minutes, once, ONLY in the app whose box first opens after it arrives (`smsCodeApp`, claimed in `showSmsCode(boxIsOpen)`); the strip clears when the box
  closes and after 2 minutes. (Version 26 used `lastApp` and the code stopped appearing; do not go back to that.)
  Never store the SMS text. Not tested on a phone.
- Version 27: the SMS code button lives in its own `smsRow` (Android's inline-suggestion updates were wiping it via `clearOtp`), and
  it also shows in password-type boxes. The Bolchi app shows "Last text seen by Bolchi: time (code found?)" under the SMS button
  to tell "no SMS broadcast" from "no code recognised". Ask Tara what that line says if the code still does not appear.
- Emoji suggestions (version 31, automatic): `EmojiHints` + `assets/emoji.txt` (built from Emojibase data via a script; ~2800 words).
  Typing a whole word that has emojis (e.g. "family") puts up to 2 emojis in the suggestion slots (right, then left; never over her saved
  words and not while a typo fix is offered); tapping inserts " 😀 " after the word. Saved names (`myWords`) stay automatic, no switch.
- Version 32 (names): `Words.fix` never auto-corrects a word typed with a capital letter (names like Tara were turned into "tar" and never
  learned); saved names (`myWords`) are offered from 2 typed letters. Tara said "name suggestion not working" on version 31.
- Version 33: on the ab→বাং / ab→हिं rows (`paintCands`) her saved names (`myWords`) and learned words that start with the typed
  English letters are added as candidates; keeping the raw English letters there learns them. Tara said names still did not
  show on version 32 (EN keys logic was tested fine locally) — ask which layout she was on if it persists.
- Phone book (version 34): READ_CONTACTS asked on the setup screen; `loadContactsSoon()` reads contact display names into memory only
  (`Words.setContacts`, never saved or sent), refreshed every 10 min. Name parts are offered by `Words.complete` (after her own words,
  max 2) and in the Bangla/Hindi candidate rows, and are never auto-corrected. English letters only (Bengali-script names ignored).
- Version 35: full phone book names. Typing 2+ letters of any word of a contact name (e.g. "Ritu" for "Emami Rituparna") shows every
  matching full name in a scrollable row above the keys (`contactScroll`, `Words.contactNamesFor`, up to 10, names starting with the
  letters first); tapping one replaces the typed letters with ONLY the matching word (Tara: "Rituparna only, not Emami Rituparna"); the chip still shows the
  full name so contacts can be told apart. EN keys only.
- Version 37: it learns her writing. `Words.record` counts every word she finishes on the EN keys (not password/email/web boxes) and which
  word followed which; words used 2+ times come first in the top row while typing (`usedMatches`), words used 3+ times are never
  auto-corrected, and after a space the top row predicts her usual next word (`nextAfter`, pair seen 2+ times). Saved in prefs `usage`
  (top 3000 words and pairs, written every 25 words and when the box closes), local only.
- Version 39 (replaces 38's rule): the phone book name row (`paintContacts`) appears only when English predictions have nothing to offer
  (no dictionary word starts with the typed letters) or the word has a capital letter mid-sentence (`nameLike`). Tara needs English
  predictions to keep working and keys must not shift under her finger. The row REPLACES the word row (same height) instead of adding a row, so the
  keyboard no longer jumps. Tara: "typing anything, it is [bringing] the name".
- Version 40: her own phone numbers. Typing phone/mobile/number/whatsapp/contact/mob/cell/tel/num on the EN keys offers her numbers in the
  top row (up to 3, tap inserts " number "): from the "My phone numbers" box in the Bolchi app (pref `myNumbers`) and from the phone book
  "Me" card (`ContactsContract.Profile`, needs the contacts permission; may be empty). Her message was cut off ("my phone number in a t"),
  so ask if she meant something else. Never put real numbers in the repo.
- Version 41: typing 3+ digits that begin one of her saved numbers (with or without +91) offers the whole number in the top row; tap
  replaces the typed digits (`numTyped`, `numbersStartingWith`, `slotNumFill`). Number/phone boxes get the strip too (`numberStrip`) and there
  the number is inserted as digits only. Tara: "pressing 983 on other websites or Zoom, it is not coming".
- Version 42: auto-correct was turning Banglish into English on the EN keys ("bhat" → "beat", so her words were never learned and no
  predictions appeared after "ami bhat khabo" typed 3-4 times). `Words.fix` now skips Banglish-looking spellings (`looksBanglish`: bh/kh/gh/jh/dh
  starts, "aa", verb endings bo/chi/cho/che/lam...) and the protected word list (`DESI`) is longer.
- Version 43: GIF suggestions (Giphy). Free key pasted in the Bolchi app (pref `giphyKey`, "Save and test GIF key"). When a typed word has emoji
  (`EmojiHints`) and a key exists, `maybeGifs` waits 450 ms, searches Giphy (`Gifs.search`, rating g) and shows up to 6 still thumbnails in `gifScroll`
  above the keys; tap sends the GIF with `InputConnection.commitContent` via `GifProvider` (content://com.tara.bolchi.gifs, cache dir), or a
  link if the app takes no pictures. Only the typed word and her key go to Giphy. Not tested on a phone; the extra row can move the keys.
- Version 44: GIFs were slow (Tara). Now: 200 ms wait, Giphy `bundle=messaging_non_clips` + limit 6 (smaller answer), the row shows at once with
  empty frames and each thumbnail fills in as it arrives (parallel), searches and thumbnails are cached in memory (`Gifs.FOUND`, `Gifs.PICS`),
  and a very big GIF (>1.5 MB) is swapped for the small one when sending.
- Version 45: the "Save and test GIF key" button now prints timings (search ms, first picture ms) so we can see what is slow on her phone, and
  the keyboard warms the Giphy connection when a box opens (`Gifs.warmUp`, at most every 10 min). Tara also said "the Samsung gifs are not
  coming": Samsung's own GIF button is Samsung's keyboard, outside Bolchi.
- Local compile check: javac against Maven `com.google.android:android:4.1.1.4` jar (ignore org.json / checkSelfPermission errors).

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
