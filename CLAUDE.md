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
- No Android SDK in Claude's workspace: only GitHub can compile. Pure-Java parts (`Words`, `EmojiHints`, `Calc`, `Spoken`, translit) can be tested locally with `javac`.
- Latest release: **app version 84 = build-100** (15 Nov 2026). Link pattern: `https://github.com/banerjeetara-tech/bolchi-keyboard/releases/download/build-N/bolchi-keyboard.apk`.
- Cloud sessions work on their own branch (`ccr-...`). To compile-check without a release, run the workflow on that branch (GitHub Actions `workflow_dispatch`
  of `build.yml`, about 1 minute). To release, fast-forward `main` to the branch. A head commit with `[skip ci]` makes NO build, so the last commit pushed to `main` must not carry it.

## Files (app/src/main/java/com/tara/bolchi/)
- `BolchiIME.java` — the keyboard: layouts, emoji panel, converter panel, voice, themes, problem report.
- `SetupActivity.java` — launcher app: mic permission, enable/switch keyboard, AI key box, problem report with Copy.
- `Translit.java` — Bengali script to Banglish (rules + word list). `HindiTranslit.java` — Hindi to Hinglish.
- `ScriptKeys.java` — Bangla/Hindi letter layouts; top row shows full vowels at word start, vowel signs after a consonant.
- `Phonetic.java` — type English, get Bangla/Hindi suggestions (inputtools.google.com, no key).
- `Words.java` — English suggestions + auto-correct (word list in `app/src/main/assets/words.txt`, Banglish/Hinglish words protected).
  `Spoken.java` — voice self-correction ("five no sorry six" → "six", "scratch that").
  `Swipe.java` — gesture typing decoder.
- `Converter.java` — converter options and all Gemini prompts (tidy, polish, tone, replies). `Themes.java` — 76 themes. `Emojis.java` — emoji tabs.
- `EmojiHints.java` — emoji for a typed word (`assets/emoji.txt` + `ALIAS` short forms / Banglish + word endings) and emoji search.
  `Gifs.java` / `GifProvider.java` — Giphy. `FontStyles.java` — Unicode fonts. `Calc.java` — sums typed in the text. `SmsCodeReceiver.java` — SMS codes.

## Features of the first builds (app version 9 / build-13) — later versions are listed below, newest last
- Bottom bar: palette (cycles themes), ⇄ converter, Bangla / Hindi / English chips, Speak.
  Tapping the already-selected Bangla/Hindi chip switches voice output to script (বাংলা / हिंदी) and back.
- Voice keeps listening through pauses until Stop (restarts Android SpeechRecognizer each round).
- Layout key next to the smiley cycles: EN → ab→বাং → ab→हिं → বাং keys → हिं keys.
- Number row, Samsung-style !#1 → 1/2, 2/2 symbol pages, emoji panel. GIFs: see version 43 below (she first said Samsung only, then asked for GIFs beside the emoji suggestions).
- Keys type on touch-down with press highlight; touch area covers the gaps. Bigger lowercase letters, wide space bar.
- Long-press space opens the keyboard picker. `onEvaluateFullscreenMode` returns false.
- 76 themes (16 more light ones added in version 72): her original 27 (including the bold dark ones — she wants to KEEP those), "Plum Lavender" (the colours of the grammar-tab preview she liked) and 32 newer LIGHT ones (she said: keep adding new ones in light mode, as many as possible). She asked for no MORE dark themes ("I don't need dark anymore", then "keep all old ones"). `Themes.OLD` keeps the 43-name list so a saved theme index still maps to the same theme; prefs `themeName` stores the name. Do not add new dark themes: 10 bold (black combos, navy/coral, plum/peach, forest/cream/gold, teal/white/yellow) + 17 light.

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
- Version 44: GIFs were slow (Tara). Now: 200 ms wait, limit 6 (the `bundle=messaging_non_clips` request option made the list come back EMPTY on her phone in version 44-45: removed in 46; sizes are now picked tolerantly, `Gifs.lastShape` shows what Giphy sent), the row shows at once with
  empty frames and each thumbnail fills in as it arrives (parallel), searches and thumbnails are cached in memory (`Gifs.FOUND`, `Gifs.PICS`),
  and a very big GIF (>1.5 MB) is swapped for the small one when sending.
- Version 45: the "Save and test GIF key" button now prints timings (search ms, first picture ms) so we can see what is slow on her phone, and
  the keyboard warms the Giphy connection when a box opens (`Gifs.warmUp`, at most every 10 min). Tara also said "the Samsung gifs are not
  coming": Samsung's own GIF button is Samsung's keyboard, outside Bolchi.
- Version 47: her GIF test on version 46 read "6 found, search 563 ms, first picture 1275 ms" (network, mostly the first connection to Giphy's picture
  server). `Gifs.warmUp` now also fetches 2 thumbnails to open that connection, every 4 min at most; GIF wait 150 ms. Do NOT add speculative
  searches: Giphy's free beta key allows about 100 calls per hour.
- Version 48: the GIF row no longer makes the keys jump (Tara: "keyboard is jumping right away and showing that gif"). When GIFs arrive they
  REPLACE the word row (`sugRow` hidden, `gifScroll` 40 dp high, same as the row) with emoji chips first then 40 dp thumbnails, exactly like the
  phone book names row. Until the GIFs arrive the word row with its emoji slots stays. `hideGifs()` brings the word row back.
- Version 49: grammar help. The ⇄ converter has two new choices, "Fix my grammar" and "Rewrite it better" (`Converter.polish`, Gemini, same language
  and script, nothing translated); holding ⇄ opens it on "Fix my grammar". It works on the whole text in the box (or the selection) and
  "Replace my text" swaps it in. Needs the AI key. Tara asked to keep her token use low: she wants this built in one go.
- Version 55: theme picker. Tap 🎨 = a scrolling grid of every theme as a colour square (`buildThemes`, `PAGE_THEMES`, ✓ marks the current one);
  hold 🎨 = the next theme (the old behaviour, `nextTheme`). No "Back" button (Tara: no additional button): tap a colour or tap 🎨 again. `applyTheme(idx)` saves `theme` + `themeName`.
- Version 57: like Gboard. The Bangla / Hindi / English tabs now ALSO set the typing layout (`setLayoutTo`: Bangla → ab→বাং, Hindi → ab→हिं,
  English → EN; if she is on the Bangla/Hindi letter keys (3/4) they stay), and the English letters of the word being typed follow the switch
  (type "ami" then tap Bangla/Hindi/English). The layout key uses the same function. Converter got "To Hindi script" (option 6, Hinglish → Devanagari
  via inputtools). A second tap on the selected Bangla/Hindi tab still toggles voice output script.
- Version 58: the converter panel is a compact grid of 8 small tabs, four per row (`ORDER` in `buildConverter`), after Tara said the lone Hindi
  option looked odd: Translate to English, Translate to Hindi (new, option 7: Google Translate `googleTranslateTo`, AI fallback), To Bangla script,
  To Hindi script, To English letters, Units and currency, Fix my grammar, Rewrite it better.
- Version 59: fonts in the converter (`FontStyles.java`, 23 Unicode look-alike styles: bold, italic, script, fraktur, circled, small caps, upside down,
  strikethrough...). New tabs Bold / Italic / Bold italic (options 8-10) and "Font style" (option 11) which shows a scrolling row of every style as a chip
  (`fontStyle`); all local, no internet. Result box + "Replace my text" as usual. 12 tabs now (3 rows of 4). Bangla/Hindi letters are left unchanged.
- Version 60: (1) fonts apply AT ONCE when picked (Bold/Italic/Bold italic tabs and the Font style chips call `replaceText`; `FontStyles.plain` turns
  already-styled text back to plain letters first so another font can be chosen; the button says "Done"). (2) After an auto-correct the top row keeps
  her ORIGINAL word (quoted) and the CORRECTED word (bold) until she types on (`fixShownTyped/Done`, `slotFix`): tap the original to put it back
  and learn it. Tara: "keep original / correct word in the tab with bold so that I can understand which one is correct".
- Version 61: a wrong word she has typed many times must not become "hers". Protection now comes ONLY from `Words.KEPT` (explicit: tapped her original in the
  top row, backspace-undo, saved names, migrated old ownWords; prefs `keptWords`, `keep()`), no longer from usage counts or auto-learned words.
  A word she typed before (`autoReplaceOk` false) is left as typed but the corrected word is still offered in bold (`fixPrompt`): tap the bold word to
  swap it in, tap her original to keep it forever. Tara: "even if I use wrong words multiple times... it will show the wrong one".
- Version 62: font MODE. Choosing Bold / Italic / Bold italic or a Font style chip in the converter also sets `typeStyle`, so everything she types next on the EN keys
  comes out in that font (`commit` swaps each letter/digit via `FontStyles.apply`; word suggestions step aside); the space bar shows the font and "(font on)".
  The first chip "Normal" (fontStyle -1) turns it off and restores plain letters. Not for upside down / strikethrough / underline. In memory only.
- Version 63: the top row while typing a word has a fixed meaning (Tara: "the predictive one will show the corrected one in bold, and mine will come as from the memory"):
  MIDDLE slot in BOLD = the keyboard's own correction (`Words.fix`) or dictionary prediction (`dictCompletions`); LEFT/RIGHT, normal weight = HER word:
  what she typed (quoted, when a correction exists) or words from her memory (`memoryMatches`: most used, learned words, contacts).
- Version 64: strikethrough and underline are font modes too (Tara asked why not): `FontStyles.apply` marks spaces as well so the line runs on, `commit` styles a typed space in
  those modes, and backspace deletes letter+mark together (`marked` in `deleteOne`). Only upside down stays a one-time conversion.
- Version 65: font mode no longer switches off suggestions and auto-correct (Tara: "auto correct is not working in case of selecting font"). Letters are tracked plain in
  `word` but WRITTEN styled (`shown()`); every place that deletes or matches the word in the text (`wordIsThere`, `endWord`, `pickSug`, `undoFix`, fix taps,
  `pickContact`, swipe) uses `shown(w)`; backspace in font mode just drops the last tracked letter.
- Version 66: the rest of the keyboard works in font mode too (Tara: "all the settings like GIF, emojis should also come"): emoji + GIF suggestions (word tracked plain),
  phone number after digits (`numTyped` fed by styled digits), next-word prediction, saved words (emails stay unstyled). Voice dictation is NOT styled (the AI tidy would break it).
- Version 67: Tara's italic-font message came out half plain / half italic and she said correction, emoji and GIF suggestions were not coming. Likely cause: the Bangla/Hindi tabs
  (since version 57) switch to the phonetic rows, where English suggestions do not exist and raw English letters were committed unstyled. `flushPhon` and `pickCand` now
  commit through `shown()`. English corrections / emoji / GIFs need the ENGLISH tab (layout 0); tell her so. If it still fails with the English tab dark, get her Bolchi version first.
- Version 68: ROOT CAUSE of "correction / emoji / GIF not working in font mode": `onUpdateSelection` treated the 2-character styled letter as a cursor jump (> 1) and wiped
  the tracked word after every letter. The jump threshold is now 2 when `typeStyle >= 0`. (Earlier guesses in versions 65-67 were wrong.)
- Version 69: styled letters are NOT real letters, so Outlook folder names / search did not match them (Tara). The font now applies only in the app where she chose it
  (`typeStyleApp`) and never in search / address / email / password / filter boxes (`plainBox`: plainField, noFix, FILTER, IME action SEARCH or GO); all font logic goes through
  `fontOn()`. Grammar fix / rewrite / translate send PLAIN letters to the AI (`FontStyles.plain`) and re-apply the font afterwards (`FontStyles.detect`) — she said the font was
  gone after she replaced a sentence.
- Version 70: Tara wants the font to stay in EVERY app ("why can't the font stay everywhere"): `fontOn()` no longer checks the app (`typeStyleApp` is still set but unused); it is still off in `plainBox` fields (search, email, URL, password, filter, Search/Go action).
- Version 71: auto-correct rebuilt (Tara: "not working always"). Causes found: only ONE wrong letter was handled; the first word of a sentence was never corrected
  (auto-capital looked like a name); `words.txt` has misspellings in its tail ("thr", "untill") that counted as real words; an uncorrected typo was learned
  as her word and then protected for ever. Now `Words.fix(typed, autoCap)` scans the list with a finger-slip distance (`slip`: neighbour key, doubled letter, swapped
  letters, wrong vowel are cheap; two slips allowed from 5-7 letters up), a table of ~200 known misspellings/contractions/missing spaces (`FIXED`), `split` for
  "thankyou"; learned words (`own`) no longer block a correction (they only stop the automatic swap). A rare dictionary word is only OFFERED (`sureFix`), and right after a
  Banglish/Hinglish word (`desiRecent`, `Words.isDesi`) corrections are offered, not made. `wordAutoCap`/`shiftAuto` tell the keyboard's capital from hers.
  Local test on 110 typos: 110 fixed (old code 99); a Banglish sample of 153 words: 0 changed (old 18); unseen Banglish words still change about as often as before. ~0.5 ms per word on a PC.
  Her question "add your intelligence": Claude cannot run inside the keyboard; her Gemini key could check a whole sentence after a full stop (not built: costs a wait and data).
  Also in 71 (Tara: "improve own intelligence in some feature as well"): next-word guesses work from day one (`Words.USUAL`, ~200 everyday pairs such as
  thank → you, good → morning; her own pairs still come first) and days, months, cities, languages and app names get their capital (`Words.PROPER`: monday → Monday,
  kolkata → Kolkata; not "may"/"march"). Maven is blocked in Claude's cloud workspace: compile-check by running the workflow on the session branch (workflow_dispatch).
- Version 72 (all asked by Tara in one message; not tested on a phone):
  - Emoji / GIFs "not working always": they only showed for an exact word in `emoji.txt` and vanished the moment she pressed space. Now `EmojiHints.key` also
    understands short forms and ~300 everyday / Banglish / Hinglish words (`ALIAS`: lol, meeting, salary, bhalobashi → love) and word endings (cakes, laughing, loved);
    and the emoji + GIFs of the word just finished stay after the space until she types on (`justEnded`; tap puts the emoji after the space). GIFs search the plain English word.
  - Calculator: a sum typed without spaces (`Calc.eval` on `token`: 250*12, 45x3, 200*15%) shows "= answer" bold in the middle; tap adds "=3000". The word row is now also
    built on the !#1 symbol pages (EN layout) so the answer shows there.
  - Date / time: typing today / tomorrow / yesterday offers the date on the right (tap adds it after the word); date / now / time are replaced by the date or time (`dateFor`).
  - Converter: new tab "Change tone" (option 12, `Converter.tone`, chips `Converter.TONES`: Write it for me, Formal, Friendly, Short, Polite, Email, Funny; Gemini). Grid rows are now 5 / 4 / 4.
  - Voice: `Spoken.punctuate` ends each spoken stretch with ? (question words, also Banglish/Hinglish and script), ! (wow, congrats, happy ...) or a full stop / danda
    (3+ words). Runs in every voice mode, on by default because she asked ("should be done automatically based on the tone"); real tone of voice cannot be heard, only the words. The AI tidy prompt also picks marks by meaning.
  - 16 more light themes (76 total): Peach Sorbet ... Morning Mist.
  - She did not understand "slide on the space bar to move the cursor" (not built). Asked: difference "Translate to Hindi" (changes the language: English/Bengali → Hindi) vs
    "To Hindi script" (same Hindi words, only the letters change: "kya hua" → "क्या हुआ").
- Version 73 (Tara picked these from a list; she saw a picture preview of the clipboard tab and said "Okay add"; not tested on a phone):
  - Emoji panel bottom row has three new tabs after the emoji groups: 📋 clipboard (`buildClips`: last 10 copied texts from `noteClip`, prefs `clips` / `clipPins`,
    tap pastes, pin keeps, ✕ removes; texts marked sensitive are skipped), 💬 replies (`buildReplies`: "✨ Reply to copied message" = 3 AI answers via `Converter.replies`,
    "＋ Save my text here", her ready messages in pref `quickReplies`), 🔍 emoji search (`emoSearch`: the letter keys come back, typed letters go to `emoQuery`, results
    replace the word row via `paintEmojiSearch` / `EmojiHints.search`; space, ✕ or backspace on empty ends it). No new button on the keys (she does not want extra buttons).
  - Undo: text removed with backspace is kept in `deletedBuf`; after 4+ characters the LEFT word slot shows "↶ Undo" (`slotUndo`) until she types again.
  - Hold backspace and slide left: `deleteWord` removes a whole word for every 28 dp (handled in `KeyArea`, `delKeyView`).
  - Banglish / Hinglish typing help on the EN keys while she is writing Banglish (`desiRecent` > 0): `Words.desiCompletions` in the bold middle slot, `Words.desiFix`
    offers the nearest Banglish word for a slip ("korhci" → korchi), never swapped in automatically.
  - Not built: shortcuts, slide on space bar, keyboard size.
- Version 74: "today" / "time" showed emoji and GIFs instead of the date (the GIF row replaced the word row, because those words have emoji). `paintSugs` now
  skips GIFs while a date, time or sum is in the word row. Tara did not understand the text steps for the 📋 💬 🔍 tabs: explain with pictures, not lists.
- Version 75 (Tara: "why anything is not working like auto correct in other app or search"; she did not say which app). Two likely causes fixed, unconfirmed:
  (1) browser address / search bars are URI boxes, where the word row, corrections, emoji were all off (`plainField`). Now `uriBox`: words are suggested and a correction
  is OFFERED in bold but never swapped in (web addresses must not change); a dot gets no space; no emoji/GIFs there. Email and password boxes stay off.
  (2) `onUpdateSelection`: browsers and web pages report several typed letters as one big cursor move, which wiped the tracked word (so no suggestions / corrections).
  A move within 400 ms of her own typing (`lastTypeAt`), or with the tracked word still before the cursor, is no longer a jump. If it still fails, ask WHICH app and box.
- Version 76 (Tara: "I need auto correct in search bar"): in a browser address / search bar (`uriBox`) a word ended by a SPACE is auto-corrected like anywhere else
  (`searchWords`: a space means search words; a piece with a dot, slash, colon or @ is a web address and is left alone). Words ended by a dot are still only offered.
- Version 77: (1) the GIF row was covering the two auto-correct choices (her word / the corrected word) the moment the corrected word had an emoji. GIFs are now
  skipped while a correction, a kept-typed word or Undo is in the word row; the emoji still shows in the right slot. (2) Calculator works for a sum that was PASTED or left
  earlier: `Calc.tail` reads the sum right before the cursor ("20+30", "20 + 30 =") when nothing is being typed, and `onUpdateSelection` repaints after a paste / cursor move.
  Dates (14/11/2026) and dashed phone numbers are not treated as sums.
- Version 78 (Tara: "if I don't select ... when will GIFs come"): after an auto-correct the GIFs come again, and the GIF row itself starts with the two choices
  (her word in quotes, the corrected word bold; chips that call `pickSug(0/1)`), then the emoji, then the GIFs. So nothing is covered and nothing waits.
  While she is still typing a word that has a correction on offer (`sugKeepsTyped`) there are still no GIFs.
- Version 79: Tara said the version 78 row (her word · corrected word · emoji · GIFs) "is not happening". Not reproduced; one real gap found and fixed: when a correction
  is only OFFERED (a typo she typed before, `fixPrompt`), `justEnded` held the wrong word, which has no emoji, so no emoji or GIFs came. It now holds the corrected word.
  Still open: ask which version the Bolchi app shows, which word she typed, and for a screenshot of the top row. GIFs need her Giphy key and the English tab; a word
  she once kept by tapping her own spelling is never corrected again.
- Version 80 (Tara, with a GIF screenshot: "if I'm sending gif the word is going below with the text" and "words are not auto correcting in long sentence, last or
  middle words; should work entire sentence and everywhere"):
  - GIF: when the box holds only the ONE word she typed to find the GIF, `sendGif` removes that word first. In a longer message the text stays.
  - Auto-correct: a wrong word she had typed 2+ times, or that sat in her auto-learned `own` list (full of old typos from before version 71), was only OFFERED, so in a
    long sentence many words were left wrong. `Words.autoReplaceOk` now swaps unless she has written the word 5+ times (known slips in `FIXED` always swap). Words she kept
    by hand (`KEPT`) are still never touched. `isDesi` no longer counts ordinary English dictionary words (rank < 15000: "mat", "din", "kal") as Banglish, which had
    switched the next two corrections to offer-only.
  - The LAST word cannot be corrected when she taps the app's own Send button without a space or full stop: the keyboard is not told. Space, punctuation and the keyboard's
    own Enter/Send key do correct it. Told her.
- Version 81: on version 80 her test sentence came back as "am really  happy  to recieve yuor mesage tommorow." ("no, same"). Reading of it: really / happy were tapped from
  the top row (hence the double spaces), and recieve / yuor / mesage / tommorow were NOT corrected although all four are in `FIXED`. The only thing that stops a `FIXED` word is
  `KEPT`: version 61 had copied her whole auto-learned `ownWords` list (typos included) into `keptWords`, and a backspace right after a correction also kept the typo for good.
  Fixes: (1) one-time `Words.cleanKept()` (pref `keptCleaned81`) drops every kept / learned / counted word that is a slip of a dictionary word; saved names come back from
  `myWords`. (2) Backspace after a correction only puts the word back the first time; the second time for the same word keeps it (`undoneOnce`). (3) A space typed right
  after tapping a suggestion adds no second space (`pickedSpace`). NOT confirmed that KEPT was the cause: if it still fails, ask for a screenshot of the top row while the
  wrong word is typed (is the bold correction shown? is "Auto ✓" on?).
- Version 82 (Tara: "autocorrect is still not working"; again no screenshot). Simulating `endWord` on a PC showed the word engine corrects her test sentence, so the
  block is around it. Fixed, none confirmed on her phone: (1) the "Auto ✓" key beside the word row switched auto-correct off FOR GOOD with one stray tap; now a tap only
  switches it on, off needs a hold, it reads "Auto OFF" in red, and it is switched back on once (pref `autoOn82`). (2) `desiRecent` (corrections only offered after Banglish)
  never wore off while she typed misspelt words and survived into the next app; now a mistyped English word counts down too, a new box resets it, and known slips
  (`Words.knownSlip`, the `FIXED` table) are swapped even right after Banglish. (3) A left-out letter cost more than a neighbour key, so salry → salty, managr → manage,
  staus → stays; `slip` miss cost 0.8 → 0.62 (local list: 111/113 typos, was 106; Banglish sample unchanged). (4) The Bolchi app now shows under "Installed version":
  "Auto-correct: ON/OFF" and "Last word not corrected: word → fix: reason" (pref `fixWhy`). If she reports it again, ask for a screenshot of that line FIRST.
- Version 83 (Tara, one message: "reply with ai is not working under emoticon", "typing of emoticon is not working", and name picks; nothing tested on a phone):
  - Phone book names: tapping a name chip no longer writes at once. The row then shows the choices for that person (`pickContact` → `writeContact`): the one name she
    was typing (bold), the full name, and the full name with each phone number (`contactNums`, read from the phone book into memory only, max 3 per person). A person
    with a one-word name and no number is still written at once. Her words "it will directly go to the recipient" were read as "write it into the message"; ask if she meant more.
  - AI replies (💬 tab): no fault found by reading. The tab hid the real reason behind "Could not reach the AI"; it now tries twice, shows the real message
    (key, limit, error type), shows which copied text it is answering, and a ⚠ note is no longer typed into the box when tapped. Ask for a screenshot of the ⚠ line.
  - Emoji search (🔍 tab): no fault found (`EmojiHints.search` works on a PC); only the hint text changed. NOT fixed: ask what she sees after tapping 🔍
    (does the "🔍 type a word" chip appear above the keys?) or whether she meant emoji suggestions while typing a word.
  - Units and currency (Tara: "also not working", no example given). Gaps found in `Converter.units`: "500 rs", "500/-", "1,500" (read as 1.5), "₹1,00,000",
    "5 lakh", "2 crore", "50k", "kms", "30 degree" and digits typed in a font were not understood; the single rate service (open.er-api.com) had no fallback.
    Now all of those are read, rates try three services (`usdRates`: er-api, frankfurter, jsdelivr currency-api) and are kept 6 hours. A bare "5 in" is no longer inches.
    Tested on a PC with made-up rates; the rate services could not be reached from Claude's workspace, so the two fallbacks are untested.
- Version 84 (Tara: "suppose i press 10rs it will show 10 dollar if i want to convert, similarly for all other currency available with sign"): an amount of money typed
  in one piece on the EN keys ("10rs", "rs10", "₹500", "$20", "50usd", "20eur", "100aed", "5lakh"; `Converter.isMoney` on `token`) fills the word row with the same
  amount in other money (`Converter.money`, `slotMoney`): rupees → $ (bold, middle) € £; anything else → ₹ (middle) first. A tap REPLACES the typed amount with the tapped
  one (her word was "convert"; the calculator appends instead, so ask if she wanted "10rs = $0.11"). Rates come in the background (`fetchRatesSoon`, at most one try a
  minute, kept 6 hours), so the first time the row appears a moment late and needs internet. Not tested on a phone.
- Local compile check: javac against Maven `com.google.android:android:4.1.1.4` jar (ignore org.json / checkSelfPermission errors).

## iPhone version (asked 14 Nov 2026, nothing built)
- Tara asked for Bolchi on iPhone. Told her: it must be rewritten in Swift; Apple needs a paid developer account (about ₹8,700 a year) which she does not have; Apple blocks
  the microphone inside keyboards (voice would have to go through the Bolchi app or iPhone's own mic button), SMS reading, and direct GIF sending.
  Offered a free web page (speak → Banglish → copy) as a first step; she did not answer and moved on to Android features. Do not start iPhone work unless she asks again.

## Waiting for Tara's test results (versions 71-74, none confirmed on her phone)
- She got a 30-point checklist. So far she reported only: date/time showed emoji instead of the date (fixed in 74), and that she could not follow the text steps for the
  📋 💬 🔍 tabs (a picture guide was sent). Everything else in 71-74 is unconfirmed.
- Ideas offered and NOT chosen: shortcuts ("addr" → full address), slide on the space bar to move the cursor (she did not understand it), keyboard size / one-handed.
- Offered and not answered: checking a whole sentence with Gemini after each full stop (meaning-level correction such as their/there).

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
- She wants a visual preview before design changes are pushed; small fixes can ship directly. Previews and how-to guides work best as a PICTURE sent in the chat
  (HTML drawn with Playwright to PNG); long numbered text steps confuse her.
- She asked to keep token use low and dislikes long waits; say up front that a build takes about 4 minutes.
- Give the direct APK link after each release; install path: Chrome → My Files → Downloads → newest file → Update.
