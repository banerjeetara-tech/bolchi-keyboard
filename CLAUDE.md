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
- Latest release: **app version 130 = build-195** (17 Nov 2026). Link pattern: `https://github.com/banerjeetara-tech/bolchi-keyboard/releases/download/build-N/bolchi-keyboard.apk`.
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
- `Converter.java` — converter options and all Gemini prompts (tidy, polish, tone, replies). `Themes.java` — 116 themes (20 see-through, 20 soft colour combinations). `Emojis.java` — emoji tabs.
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
- Version 85 (Tara: "add transparent keyboard under colour palette", then "as many as posb" after the picture preview): 20 see-through "Glass" themes at the END of the
  theme list (96 total; a ◇ marks them in the 🎨 grid): 16 light tints (Glass Clear, Rose, Peach ... Ocean) and 4 smoked ones with white letters (Smoke, Navy, Plum,
  Forest; needed over dark apps, and shown in the preview, although she asked for no more dark themes: remove them if she objects). They are ordinary `Themes.HEX`
  rows written as #AARRGGBB (`Themes.glass`). NOT tested on a phone: what shows through is decided by the app (many show a plain colour behind the keyboard), and
  it is not known whether Samsung's keyboard window lets the app show through at all. Ask her for a screenshot.
- Version 86 (Tara: "have you add all currency?"): 28 currencies (`Converter.CURRENCIES`: INR USD EUR GBP AED SGD AUD CAD JPY CNY CHF SAR QAR KWD OMR BHD BDT NPR LKR PKR
  THB MYR HKD NZD ZAR KRW IDR RUB; codes, names like yen / taka / riyal, signs ¥ ৳ ₩ ฿). The money row is now a sideways-scrolling chip row in the place of the word row
  (it reuses `contactBox` / `contactScroll`; `pickMoney`): every other currency with its sign, ₹ first for foreign money, $ first for rupees. The Units tab shows rupees as
  $ / € / £ / AED and foreign money as ₹. "try" (Turkish lira) is left out because it is an English word. She also restated the three name choices (only name, name with
  surname, full name with number): that is what version 83 does (a second phone number adds a fourth chip).
- Version 87 (Tara: "10rs = how many dollar? ... I don't know the currency ... go to converter, it will show the currency name with country, whichever I select
  will come to the chat"): ⇄ → "Units and currency" with money in the text now shows a scrolling LIST instead of the result box (`Converter.moneyList`, `LANDS`):
  flag, currency name, country, and today's value for the last amount in the text (27 rows). A tap writes "= value" after her text ("10rs = $0.11") and returns to
  the keys. Rates are fetched first if missing ("Getting today's rates…"; a failure shows the usual ⚠ box with Try again). Text without money still gets the old result box.
- Version 88 (Tara: "use colour palette with 3 combination, 4 combination, coloured combination"): 24 bright "Trio" / "Quad" themes were added without a preview and she
  called them awful; removed in version 90 (see there). What stays from 88: a theme row in `Themes.HEX` may carry a 7th value = number row colour (`Themes.numberRow`,
  `keyFill` in `charKey`), and `Themes.combo` marks the combination themes in the 🎨 grid.
- Version 89 (Tara, by voice: "again check the auto correct, it's not working for all which I am typing; and while speaking it is sometimes giving a pause [a full stop]
  if I pause, not necessarily it is a proper pause"). Still no screenshot of the "Last word not corrected" line, and it is not known whether she installed 82+.
  - Auto-correct: a list of 231 real-life mistakes showed the gap: true misspellings were fixed, but chat SHORT FORMS she uses all the time were not (u, ur, r, hv, bt, nd,
    dnt, cnt, gud, mrng, tym, offc, mtg, rprt, amt, posb ...). About 60 of them are now in `FIXED` (217/231 fixed, was 157). They are also listed in `Words.SHORT`:
    right after Banglish / Hinglish they are only offered, because "r", "bt", "k" are words there. Misspelt days / months get their capital (wensday → Wednesday).
    If she dislikes "u" becoming "you", remove the entries from `FIXED`.
  - Voice: every recogniser round (about 2 s of silence) ended with a full stop from `Spoken.punctuate`, also when she only paused to think. Now a round gets only ? or !
    (`punctuate(text, false)`); the full stop is owed (`stopDue`, `Spoken.wantsStop`: 3+ words, not ending on a joining word such as and / to / ar / aur) and is written
    only when she speaks again after 2 more seconds of silence (`newLead`) or presses Stop. The AI tidy still punctuates by meaning. Not tested on a phone.
- Version 90 (Tara, with two screenshots: the soft "Mint" keyboard = "really beautiful", "Quad Festival" = "awful"; by voice: full stop when she stops in between,
  repeated words stay, the engine writes a different word for what she says):
  - Themes: the 24 Trio / Quad themes of version 88 are REMOVED (bright keys in clashing colours; never do that again). In their place 20 SOFT combinations in the Mint
    style: soft background, WHITE keys, side keys in a second soft colour, one strong accent ("Mint & Peach" ... "Pistachio & Plum"); the last 8 ("Mint, Peach & Lemon" ...)
    give the number row a pale third colour. 116 themes. A saved theme that no longer exists falls back to Mint, not black. Picture sent with the build.
    Lesson: she judges colour by her Mint keyboard; send the picture BEFORE building colours.
  - Voice full stop: now written by a timer (`writeStop`, `endSentence`): 1.5 s after a listening round ends with a whole sentence, i.e. about 3.5 s of silence; speaking
    again sooner cancels it (`newLead`); Stop writes it at once. (Version 89 wrote it only when she spoke again, which she read as "after stopping it is giving the pause".)
  - Repeats: `Spoken.dedupe` removes a group of 2-5 words said twice in a row in every voice mode, and a single repeated word on the English chip only (Bengali / Hindi
    double words on purpose: aste aste); `Spoken.dropRepeat` drops the words she repeats after a pause. "very very", "no no" stay.
  - AI tidy kept only a leading space of the spoken text; a leading full stop / other head is now kept as it is (`head` in `tidyApply`). Her dictated message showed
    "stoppedAnd" and "removing.fter" (lost space / letter at a round boundary): cause NOT found, watch for it.
  - Wrong words from the speech engine ("pause" → "cause", "pauE"): that is the phone's engine, not Bolchi; the AI fix (hold Speak) repairs some. Told her.
- Version 91 (Tara: "in Bengali typing it's unable to pick English words"): on the ab→বাং / ab→हिं rows (`paintCands`) English words for the typed letters now stand
  right after the first Bangla / Hindi word: the word itself when it is in the dictionary, else its corrected spelling (`Words.fix`), plus the likeliest longer word
  (`Words.dictCompletions`); the row is capped at 6 + the raw letters. Space still takes the FIRST (Bangla / Hindi) word. Not shown for Banglish words (`Words.isDesi`).
  Also in 91: "wat", "hav", "coz" are corrected again (an old line had taken them out of `FIXED`; they are in `Words.SHORT` now, so after Banglish they are only offered).
  If she meant that space should write the English word by itself, ask first: on the Bangla tab "office" is normally wanted as অফিস.
- Version 92 (Tara: "this English to Bangla I meant for both typing and speaking"; "it is still taking pause immediately and not removing if I start speaking again";
  "very poor, like something cheap"):
  - Speaking on the Bangla chip: `Translit` got about 400 more English words as the Bengali engine writes them (`LOAN`), and Bengali endings on them are split off
    (`ENDS`: "অফিসের" → "office er", "মিটিংটা" → "meeting ta", "ফাইলগুলো" → "file gulo"). Words not in the list still come out by sound; only the AI fix repairs those.
    Never match by sound alone: Bengali words collide with English ones (pore, kore, more, boi).
  - Typing on the Bangla / Hindi tab: a usual English word of 5+ letters that is not Banglish (`Words.clearlyEnglish`: office, meeting, please, report) goes FIRST in the
    row and space writes it in English; shorter or doubtful words are still only offered second.
  - Voice full stop, again: the likely source of the IMMEDIATE full stop was the AI fix (on for her), which closed every stretch with a full stop about a second after
    each pause. `tidyApply` now strips a final full stop the AI adds while she is still listening. The silence timer is 3 s after the round ends (about 5 s of silence),
    and a full stop the timer wrote is deleted again if she speaks within 15 s (`autoDot`). Stop still writes it. If full stops STILL come at once, the phone's speech
    engine is writing them itself (then strip a trailing "." from the engine text in `onResults`); ask which chip was dark and whether the AI fix was on.
  - She is unhappy with the quality (many builds, nothing verified on her phone). Fewer, checked changes; ask for a short screen recording when a report is unclear.
- Version 93 (Tara asked whether changing rates are picked up; she said yes to a fresher fetch): currency rates are kept 1 hour instead of 6 (`Converter.usdRates` / `ratesNow`).
  The free services themselves update about once a day, so "about" stays in the wording.
- Version 94 (clear themes): 30 new see-through combinations at the end of the palette: 10 with 2 colours, 10 with 3, 10 with 4 (the 4th colour is the number row). Background alpha 5%, keys 30%, function keys 36%, so the app shows through; ink is dark on light keys and white on dark keys. Appended at the end so saved theme indexes stay the same.
- Version 95 (window): the keyboard's own window was opaque, so the see-through themes could not show the app behind. `onCreateInputView` now makes the IME window background transparent; the keyboard root still paints the theme colour (alpha), so solid themes look the same. Tara's screenshot (build 118) was a solid keyboard: check which theme she picked.
- Version 96 (palette squares): palette squares showed the see-through colours almost invisible, so 2/3/4 colour combinations looked blank. `Themes.swatch()` gives each square its solid colours (background, keys, function keys, number row, each once) as hard stripes. Tara chose the last clear theme and it still looked solid on build 118 (before the window fix in 95): ask her to test build 119 or newer.
- Version 97 (Tara: "the text is still going with GIF"; "while I'm speaking in Bengali and some English words are coming in between it's unable to recognise;
  these issues already shared earlier but not resolved"):
  - GIF: version 80 removed the text only when the box held ONE word, so "good morning" + GIF still went as caption. `sendGif` now removes the whole text when it is short
    (up to 40 characters, one line, cursor at the end); in a longer message only the word the GIFs were found for (`gifTyped`) when it stands right before the cursor.
    If she wants a long message kept back as well, that needs a different idea (send text first): ask.
  - Speaking Bengali with English words, WITHOUT the AI: the converter wrote spoken English by sound ("myanejment konfarens teknolji"). New sound-matcher:
    `Translit.looksLoan` (a Bengali-script word that uses only the letters English loans use and has a tell-tale: ট ড ফ ্যা শন ক্স ন্স; not ড়, not খ ঘ ঠ থ ধ ছ ঁ ...,
    not verb endings লেন / বেন / লাম ...) → `Words.soundAlike` (consonant shape + where the vowels sit, `Words.shape`; English words of 6+ letters, rank < 20000, 4+ consonant
    sounds; an exact vowel match wins: promotion, not permission). Endings are split off (`ENDS`). PC test: 54 of 55 spoken-English samples come out right
    (management, conference, technology, university, performance, experience ...); of about 170 ordinary Bengali words / 40 sentences none turned into English after the
    fixes (কাটলেন → kathleen and ডাকলেন → decline were caught by the verb-ending rule). SHORT English words (under 6 letters) still need the explicit lists (`DICT` / `LOAN`)
    or the AI fix. Not tested on a phone; if a Bengali word turns English, add its pattern to `looksLoan`.
  - Lesson: she counts an issue as "not resolved" until it works on her phone; say plainly what is still limited instead of announcing a fix.
- Version 98 (Tara: "potty it's taking porty, pause as pase ... matching English words should be English only"; "in GIF no text should go"):
  - GIF: the box is now emptied COMPLETELY before the GIF is sent, whatever its length (`sendGif`). A text of more than 40 characters is kept (`gifHeld`) and offered
    back as "↶ My text" in the left word slot: at once, and once more when the box of the same app opens again within 5 minutes (not in a box whose hint says "caption").
  - Short English words in Bengali speech: `Words.soundAlikeShort` matches Banglish of 4-7 letters with 3+ consonant sounds against the 8000 most usual English words,
    strictly (vowels in the same places and of a fitting kind, `vowelFits`): topic, print, level, model, total, hotel, focus, start, draft, credit, limit, target, visit,
    teacher, problem ... The long match checks vowel kind too now (funding, not finding). `Translit.NATIVE_ONLY` also holds ত and দ (English t / d are always ট / ড).
    PC test: 55 of 55 long and about 52 of 60 short samples right; the Bengali samples unchanged. Words with only TWO consonant sounds (potty, pause, file, call) can
    not be matched safely: they need the explicit list (`DICT` / `LOAN`; potty and pause added) or the AI fix.
  - "porty" for potty, "pase" for pause: that is the phone's speech engine HEARING another word; no table repairs it. Trial for that: a button in the Bolchi app,
    "Mixed Bengali + English listening" (pref `langSwitch`, OFF by default): on Android 14+ `beginRound` adds `EXTRA_ENABLE_LANGUAGE_SWITCH` (balanced) with the chip's
    language + en-IN. Not known whether her Samsung engine supports it; if Bengali gets worse she turns it off there.
- Version 99 (Tara: "whenever I press any key most of the time the letters in the line below come and it is difficult to type; I do not want to increase the key size"):
  a thumb presses lower than it aims, so `KeyArea` now looks up the key a few dp ABOVE the raw touch (`keyUnderThumb`, pref `touchUp`, default 5 dp; keys keep their size).
  Not applied in the bottom row, and a tap never moves from a typing row into the word row / Bangla candidate row (`sugRow`, `candRow`). The Bolchi app has a button
  "Key touch point" (Normal / A little higher 5 / Higher 8 / Much higher 11 dp). The old hit test itself was consistent (`keyAt` = nearest key rectangle), so this is a guess
  from her words: if she says the letter ABOVE comes now, lower it. Not tested on a phone; swipe typing uses the raw path and is unchanged.
- Version 100 (Tara: "for every letter it is happening, either the upper letter or the below letter comes"): that is more than a fixed thumb offset, and reading the code found
  no drawing / touch offset (`keyAt` uses the same coordinates as the drawn rows). So the keyboard now MEASURES her taps (`learnTouch`: where a tap lands inside the letter key it
  hit, from the key's middle, running average in prefs `touchBias` / `touchN`) and by default (pref `touchUp` = -1, "Auto") lifts taps by that measured amount, 0-14 dp
  (5 dp until 30 taps are known). The Bolchi app shows "Measured on N taps: they land on average X dp LOWER / HIGHER than the middle of the key" and the button has the
  fixed steps as before. Ask her for that line first: a bias near 0 with wrong rows means the taps are simply spread wide (then the problem is not an offset).
- Version 101 (Tara, her own words: "if I speak in Bengali or Hindi and any English word [comes out wrong, it should be detected and] can be corrected later on with a single key for any English word";
  she could not say what she wanted from the converter, so this is MY reading: ask her to confirm): tap a word in the text (or finish speaking, then tap) and the cursor stands in
  a Latin-letter word that is not English, not in her words / phone book, not Banglish (`computeRepair`): the word row (EN keys, `slotRepair`) or the Bangla / Hindi candidate row
  (label "English?", `paintCands`) offers up to 3 English words that sound like it (`Words.englishFor`: a typo fix, then every word with the same consonant sounds, vowels not compared);
  one tap replaces the whole word (`applyRepair`). Works on any word, so it also repairs what the automatic matcher missed. Not for font mode, passwords, while speaking, or
  within 0.7 s of typing. Words with two consonant sounds only (potty, pause) cannot be matched: "porty" gives party / part / pretty. Not tested on a phone.
- Version 102 (Tara sent a screenshot of the Bolchi app, build 101: "Last word not corrected: “tof” → too: just after Banglish words"; "Measured on 195 taps: they land on average
  6.4 dp LOWER than the middle of the key"; "Key touch point: Normal"; "Mixed Bengali + English listening: ON"; asked "check the word issue and suggest"):
  - Word issue: `desiRecent` made every correction offer-only for 2 words after any Banglish word, so in mixed typing English slips were left. `Words.confidentFix(typed, fixed)`:
    one slip (insert / missing / wrong letter / swap) away from a word in the first 2000 of the list, typed 3+ letters, not Banglish-looking, not protected: that is now
    swapped even after Banglish (`calm` in `endWord`). 8 of 265 Banglish test words collided (giye→give, chup→cup, kon→non, besi→best, yar→year, adda→add, mukh→much, buk→but):
    added to `DESI` with ~35 more; test now 0 of 265, and 111/113 typos + 218/231 short forms unchanged. If a Banglish word turns English, add it to that DESI addition.
  - Touch: her measured bias is 6.4 dp LOWER (raw taps, independent of the setting) and she had the button on "Normal" (= no correction). Told her to tap it once ("A little higher", 5 dp)
    or to Auto (6.4 dp). Her AI key and Giphy key are visible in that screenshot: never repeat them.
- Version 103 (Tara typed Banglish: "...phone change hocche ... opose e giye ... font er dorkar nei normal continue"; she wanted "font" not "phone" and "app" not "opose", and "not getting
  any option to correct the word" — version 101's tap-to-repair showed nothing): three reasons found. (1) `computeRepair` returned while `listening`: the mic keeps listening until Stop, so
  a tap during dictation never worked; now it waits only 1.5 s after the last voice text (`lastVoiceAt`) and while `composing`. (2) A real English word ("phone") was skipped; now the
  phone's OTHER GUESSES are kept per spoken word (`EXTRA_MAX_RESULTS` 5, `rememberAlternatives`, `heardAlternatives`; only guesses with the same number of words, last 8 sentences, memory
  only) and offered for ANY tapped word, then the sound-alike English words for non-English ones. (3) Only 3 choices fitted; the EN keys now show up to 8 as a scrolling chip row in the
  word-row place (reuses `contactBox`), the Bangla / Hindi row shows 4. Limits: "app" has only 2 consonant sounds, so `Words.englishFor` still cannot find it ("opose" gives
  oppose); the guess list is the engine's, it may not contain the right word; no guesses once the AI tidy has rewritten the sentence. Not tested on a phone.
- Version 104 (Tara: "could not understand how to use this [tap a word] while speaking Bengali with English words in between; correct the same"; and "duplication of the word is still coming"):
  - She could not find / use the tap-to-repair, so the keyboard now POINTS at the word itself: after each spoken sentence `findSuspects` looks for Latin words that are no English word, not hers,
    not Banglish, 4+ letters, with choices (the engine's other guesses that are dictionary words, then `Words.englishFor` whose first letter fits), and the word-row place (EN keys) /
    candidate row (Bangla / Hindi keys) shows "opose ?  oppose  opposed … ✕". One tap replaces that word wherever it stands (`applyRepairAt`: ExtractedText + setSelection, cursor put
    back; it needs an editor that supports it), ✕ or a pick moves to the next doubtful word (`nextSuspect`, up to 4 per sentence). `repairAutoWord` != null marks this mode; typing clears it.
    The tap-into-text repair of 101 / 103 stays. Not tested on a phone; if the replacement fails in some app the word just stays (`saveProblem` on exceptions).
  - Duplicates: version 90's `Spoken.dedupe` removed a single doubled word on the English chip ONLY, so "ami ami", "tumi tumi" stayed on Bangla / Hindi. Now single doubles go in every mode except
    intended ones (`keepDouble`: English very / no / bye..., Bengali / Hindi `REDUP` list and words ending -te such as "dekhte dekhte"); also applied to the live (partial) text. She gave no example:
    ask for the exact text before and after if it still doubles (a pause between the two copies, punctuation, or the AI fix may be involved).
- Version 105 (Tara with a WhatsApp screenshot: "Duto theke half khele kota hoy Kota hoy beauty?" = a repeat after a pause; "there should be an option where I reject some Bengali words, it should be
  stored in memory"; "after every pause [a full stop] is given, instead of finding the tone and giving accordingly"):
  - ROOT CAUSE of the doubled phrase: `onResults` skipped `Spoken.dropRepeat` whenever `composing` was true (the new sentence had already begun to show through partial results), i.e. almost
    always. Now the text before the sentence is captured when its first partial arrives (`roundTail`) and used for both the live text and the final one.
  - Punctuation by meaning: no mark is written at a pause any more. Marks come only from `endSentence` (5 s of silence, or Stop): `Spoken.markFor` judges the WHOLE sentence since the last mark
    (? for question words, ! for cheers, full stop / danda for 3+ words not ending on a joining word). A mark written by the timer is removed again if she speaks within 15 s (`newLead`,
    now also for ? and !). Tone of voice cannot be heard; only the words decide. The AI tidy (if on) still punctuates by meaning on its own.
  - Memory: (a) a word she repairs (tap row or the automatic row) is stored as wrong → right in pref `voiceFix` (latest 300) and replaced by itself in later dictation (`applyVoiceFix` inside
    `convert`); (b) the "✓ keep" chip on the automatic row (it was ✕) says the word is right: `keep(word)`, never pointed at again. Her wording was unclear ("defeating" = rejecting / deleting?):
    ask if she meant that deleting a word should teach the keyboard (not built).
- Version 106-107 (Tara: "if I am using some words in Bengali it will be remembered in the memory, and next time this word will be shown or come from the memory itself"; then "both typing and
  speaking it should be remembered. If I tap, then it will go to memory"): typing already counted words (`Words.record`, offered first from 2 uses), speaking did not. Now:
  (1) every Latin word of a spoken sentence is recorded (`learnFromSpeech`, same `usage` memory, next-word pairs too); (2) a spoken word that sounds like one she uses 3+ times (same consonant
  sounds, up to 2 letters different, not English, not hers twice itself, and not in the built-in `DESI` list) is written HER way (`Words.rememberedSpelling`, in `applyVoiceFix`); (3) the
  ab→বাং / ab→हिं row offers her most used non-English words (`usedMatches`); (4) TAPS: a tapped word suggestion counts double (`pickSug`); the Bengali / Hindi word she taps for some typed letters is stored
  for those letters (`rememberPick`, pref `phonPick`, 500 latest, per layout) and comes FIRST next time and is what space writes (`flushPhon`, `paintCands`); in the tap-into-text row a "★ save" chip
  (`SAVE_TOKEN`, `saveWordToMemory`: `keep` + counts 3) puts any non-English word into memory. Misheard words are only counted, so need 2 uses to be offered and 3 to rewrite anything.
  Not tested on a phone. If space now writes a word she did not want, she taps the other candidate: that replaces the memory.
- Version 108 (Tara: "beauty is already saved in memory, why is it not coming and coming as beauti while speaking"): the spoken Bengali spelling ("biuti" / "beauti") was never matched against
  the English words she uses: the general matcher needs 3-4 consonant sounds and "beauty" has 2. `Words.personalEnglish(w)`: among HER English words (dictionary words used 2+ times in the memory) the
  one with the same consonant sounds, a fitting vowel in the same places, length within 1 and at most 3 letters different; applied in `applyVoiceFix` (dictation) and first in `Words.englishFor`
  (tap row / automatic row). PC test: beauti / biuti / byuti / bauti → beauty, miting → meeting, salari → salary; bati, hati, bhalo, tumi, korchi unchanged. Only her own words, so a new word
  needs 2 uses first. Where "beauti" comes from is not known (the converter writes "biuti" for বিউটি).
- Version 109 (Tara: "can I now save as many words in the app and it will remember" … "English and Bengali"): the Bolchi app's box 6 "My words" now takes English words, Banglish words and Bengali / Hindi
  words in their own script, as many lines as she likes (`loadSavedWords`, called from `onStartInputView`). Latin words are made "used 3 times" (`Words.ensureUsed`, idempotent) so dictation writes them
  her way (`personalEnglish` / `rememberedSpelling`) and they are offered first while typing; a Bengali / Hindi word is tied to the Latin letters of its sound (`Translit.toBanglish` /
  `HindiTranslit.toHinglish`, or the letters before "=" in a line "ami = আমি") in the same memory as her taps (`phonMem`, never overriding a pick she made) and also offered when the typed letters
  are only the START of its sound (`romanOfSaved`, `paintCands`). Script lines are no longer offered as raw text on the English keys. Not tested on a phone.
- Version 110-111 (Tara: "typing Ma / ke / niye [in English letters] the save option comes sometimes, sometimes not; I want it to appear for English and Bengali, speaking and writing"): the "★ save" chip
  only existed after TAPPING into a word of 3+ letters, so it was missing for short words, for English dictionary words, for Bengali / Hindi SCRIPT words (`isWordChar` was Latin only) and always while
  typing or with the cursor at the end of a word. 110: the tap chip now covers 2+ letters, any language, script words stored for the Latin letters of their sound (`saveWordToMemory` → `rememberPickFor`).
  111: a permanent "★" button (`starKey`, weight 0.5) in the word row beside "Auto" and at the end of the Bangla / Hindi candidate row (`paintCands` now wraps `paintCands0`): `saveCurrentWord` saves the
  word being typed (EN keys), or the word space would write for the typed letters (Bangla / Hindi keys), or `wordNearCursor` (the word the cursor is in / the word before it, e.g. after speaking).
  It reuses the earlier memory (`keep`, counts, `phonMem`). She disliked extra buttons before; this one she asked for. Not shown on full script layouts 3 / 4 (no word row). Not tested on a phone.
- Version 112 (Tara: both stars now come [the tap chip and the permanent one]; "the duplicate words deleting is not coming in Bengali as well as English, check again"): ROOT CAUSE found with a PC test
  that follows the real flow: on the Bangla / Hindi chips `dropRepeat` compared the RAW script the engine heard ("কাল অফিস যাব") with the Latin text on screen ("ami kal office jabo"), so it could never
  match: repeats after a pause were removed only in English. Now the heard text is converted first (`convert`, then `dedupe`) in both `onPartialResults` and `onResults` and the converted words are
  compared (test: before "ami kal office jabo" + heard "কাল অফিস যাব তারপর বাড়ি আসব" → "tarpor bari asbo"). `dropRepeat` also looks back up to 8 words (was 5). Still not covered: a repeat that
  differs in one word, one the AI tidy creates, or one longer than 8 words. If it still doubles, ask for the exact text before / after again.
- Version 113 (Tara: "keep the permanent button only"): the "★ save" chip in the tap-into-text row is removed (`computeRepair` adds no `SAVE_TOKEN` any more; a Bengali / Hindi script word gets no row at all).
  Saving is ONLY the permanent ★ button (`starKey` / `saveCurrentWord`). `SAVE_TOKEN` handling in `applyRepair` / `paintCands` is dead code, left in place.
- Version 114 (Tara: "calculation is not working after a long chain of numbers"): `Calc.eval` ignored a sum over 40 characters and over 19 numbers, `Calc.tail` (pasted sums) read only 44 characters, and a chain like
  "100-20-30" or "1000-200-50-25" was taken for a phone number. Now 400 characters / 128 numbers, 400 characters looked back, and digits-with-dashes is a phone number only with a group of 5+ digits or 10+ digits and no 1-2 digit group.
  PC test: a 60-number chain = 1830 (correct); 022-2345-6789, 98300-12345 and 14/11/2026 still left alone.
- Version 115 (Tara, 17 Nov: "two colour combination pack and three colour combination pack, genuine"; first preview "looking very similar", second preview "Yes"): 24 themes appended at the END of `Themes`
  (170 total; saved indexes unchanged): 12 "Two: X & Y" (coloured background + letter keys in a second soft colour, side keys deeper tone of the background hue, dark accent) and 12 "Three: X, Y & Z" (a third colour on side keys
  and number row via the 7th value). Data in `tools/colour-packs-proposal.json`. They are inside `Themes.combo`, so the 🎨 square shows stripes (background / keys / side keys / number row). NOT seen on a phone.
  Lesson: all-white keys made every pack look the same; letter keys must carry their own colour.
- Version 117 (Tara: "it can't remember one common phrase if I use anything constantly... at least twice... it should give me the next options automatically"): phrase memory. Causes found in the old next-word memory:
  (1) `exportUsage` saved only word pairs seen 2+ times, so a phrase used once today and once tomorrow never reached 2 (the count-1 pairs were lost whenever the keyboard saved / restarted);
  (2) only ONE word of context, so "kal" gave the same guess after "ami kal" and "tumi kal"; (3) one-letter words ("I", "a") were never recorded, which broke "I am going". Now `Words.record` also keeps
  three-word phrases (`PHRASE`, key "first second" -> next word, tied by the `chain1/chain2` of the last recorded words), `nextAfter` offers phrase matches first (seen 2+ times), then pairs (2+), then `USUAL`;
  all pairs (also seen once, 5000) and phrases (5000) are saved (`#pairs`, `#phrases` in pref `usage`; old saves still load). Works for typing, tapped suggestions and speech (`learnFromSpeech`).
  PC test: used once = no guess; twice = guess; "ami kal" -> office but "tumi kal" -> ghum. Not tested on a phone.
  Also in 117 (Tara: "both for English and Bengali"): the same memory for Bengali / Hindi: words written on the ab->Bangla / Hindi keys (`flushPhon`, `pickCand` -> `noteScriptWord`, `Words.recordSeq`: pair and phrase memory only, no word count)
  and words spoken as script (`learnFromSpeech`) are remembered as phrases; after a word the Bangla / Hindi row shows up to 4 next-word chips (`paintCands0`, `pickNextScript`) instead of the hint. Banglish typed or spoken in Latin letters
  already worked on the EN keys. Not on the full script keys (layouts 3 / 4: no word row).
  Auto capital with Auto-correct OFF (Tara: "auto capitalize is not coming if auto correct is off"): the capital letters were part of the auto-correct swap, so with "Auto OFF" i / i'm / monday / kolkata stayed lowercase.
  `Words.capitalOnly` (i -> I, I'm, days, months, cities, apps; only words typed fully lowercase) is now applied in `endWord` when `autoFix` is off; no other letter is ever changed. The capital at the start of a sentence (`autoShift`) never depended on Auto.
  Auto capital after deleting (Tara: "if while starting I delete anything and starting again then the auto capital is not taking"): `autoShift` only ran after a space, mark, Enter or swipe-delete, never after a plain
  backspace, so "type a letter, delete it, start again" left Shift off in an empty box. `deleteOne` now calls `shiftAfterDelete` (at once and again 90 ms later, because the app shows the shorter text a moment late), and
  `onUpdateSelection` calls it when the cursor lands at 0 (box emptied / sent). A capital she switched on by hand (shift on, not `shiftAuto`) is left alone. Not tested on a phone.

- Version 118 (Tara: "under save my note is not working"; read as the 💬 replies tab button "＋ Save my text here", `buildReplies`): no fault in the saving itself was provable by reading, but three things made it look dead:
  (1) every answer ("Type the message first", "Too long to save") went to `flashSpace`, i.e. the space bar, which is HIDDEN while the emoji panel is open, and a successful save gave no sign at all;
  (2) texts over 400 characters were refused; (3) `currentText` trusted an EMPTY `getExtractedText` answer (some apps return one) instead of asking for the text around the cursor.
  Now the answer is written inside the tab (`repliesNote`, 8 s: "✓ Saved: tap it below to write it again" or a ⚠ line saying what to do), the limit is 2000 letters (60 saved messages), and an empty extracted text falls back
  to before + after the cursor. If it still fails ask WHICH app and whether the ⚠ line shows (then that app gives the keyboard no text). Not tested on a phone.

- Version 119 (Tara: "why 3? it can be any word if I'm using twice"): the phrase memory of 117 was limited to two words of context (three-word phrases) only because that was the first thing built. Now it follows up to 5 words back:
  `Words.CHAIN` holds the last words written in a row (a sentence mark, new box or cursor jump clears it), every phrase length 2..5 that ends with the last word is counted in `PHRASE`, and `nextAfter` tries the LONGEST
  phrase seen twice or more first, then shorter ones, then the pair, then `USUAL`. So a 9-word sentence she used twice is offered word by word ("please find attached the" -> report, "report herewith for" -> your).
  Saved as "words<TAB>next<TAB>count" lines under `#phrases` in pref `usage` (6000 lines; the old 4-field lines still load). PC test: once = nothing, twice = offered, "ami kal" -> office / "tumi kal" -> ghum still differ. Not tested on a phone.

- Version 120 (Tara: "three times I rechecked this one, but still it is not giving me any predictive text in the fourth time" — the phrase memory of 117/119; no word list, no app, no layout given): the memory logic itself passes the PC test with the
  real call order (`record(lastWord, word)` per finished word), so the miss is in what the screen shows. One real hiding place found: a finished word that has an emoji (`EmojiHints`) and a Giphy key makes `maybeGifs` HIDE the word row
  (`sugRow` GONE) behind the GIF row, which carried only the correction chips, emoji and GIFs, never the next-word guesses (every everyday word like good / meeting / home / office / happy). The GIF row now starts with her next-word
  chips (`writeNextWord`, same as tapping a guess). Also new: `Words.memoryStats` and `noteNextWhy` write pref `nextWhy` ("after “x” offered [...]" / why no row: no word before the cursor, box without word row, not the English keys, ...
  plus "N of M pairs and N of M phrases seen 2+ times"); the Bolchi app shows it under "Installed version" as "Next-word guesses: ...". If she says it still fails, ask for a screenshot of THAT line first.
  Other causes NOT ruled out: `onUpdateSelection` wiping `lastWord` after a slow app reports the cursor (> 400 ms after her key), the last word of a message sent with the app's Send button (never recorded: no space), Bangla / Hindi full script keys (no row).

- Version 121 (Tara after build-174: "There is no next word guess tab" / "In app"): the "Next-word guesses" line of version 120 was missing in the Bolchi app. It was only added when pref `nextWhy` already had text, and the block was built once in
  `onCreate` and never redrawn on return. Now `SetupActivity.diagText()` ALWAYS shows the line (empty = "nothing noted yet ... older than version 120") and `refresh()` (onResume) redraws it. If it stays empty after typing words with spaces on the
  English keys, the keyboard in use is not 120+ (check the "Installed version" line) or `paintSugs` never reaches the next-word branch (`sugRow == null`). Not tested on a phone.

- Version 122 (Tara's screenshot of the Bolchi app on build-176: "Next-word guesses: this box has no word row (email / number / search box) | memory: 91 of 112 pairs and 1 of 77 phrases seen 2+ times"; she typed "ami cheye cheye dekhi saradin"
  three times and the 4th time no next words came). Reading it: (1) the `nextWhy` note is overwritten by whatever box was painted LAST, here the Bolchi app's own number box, so it did not describe her test (now notes of an empty box / a plain box never
  replace a note made while she typed words, and every note carries "[app HH:mm]"); (2) only 1 of 77 phrases had been seen twice, so the phrase chain was breaking. Causes fixed (none provable on a phone):
  - a TAPPED suggestion made `onUpdateSelection` see a cursor jump (more than one character, `lastTypeAt` not set by taps) and clear `lastWord`, which breaks every phrase after a tapped word: `pickSug`, `writeNextWord`, `pickNextScript`, `pickCand` set `lastTypeAt`;
  - the LAST word of a message sent with the app's own Send button (no space) was never remembered, so "...dekhi" never learned "saradin" (the phrase stopped one word short): `notePendingWord` counts it when the box empties (not if it looks like a typing slip);
  - a correction that was only OFFERED (very common between Banglish words: "cheye", "dekhi" have near dictionary words) filled the two left places and hid the next-word guess: the right-hand place now keeps the best next word
    (`slotNext[2]`), and the GIF row shows next-word chips after the correction chips too.
  Still open: ask for the Bolchi app screenshot again AFTER typing the sentence in a chat box (the line now says which app, when, and what it offered).

- Version 123 (Tara, screenshot on build-178 / version 122, WhatsApp 23:06: "Next-word guesses: [com.whatsapp 23:06] after “rakam” nothing seen twice yet | memory: 94 of 133 pairs and 1 of 111 phrases seen 2+ times"; "Still same"). The keyboard WAS running
  and learning in WhatsApp, but after a whole evening of the same sentence only 1 phrase had reached 2 uses (pairs 91 -> 94 seen 2+, phrases 1 -> 1): the phrase chain kept in memory (`CHAIN` / `lastWord`) is broken by too many things
  (cursor-move updates, new box, tapped suggestions, ★ save, restarts) and could not be traced without a phone. So the memory no longer depends on it: `contextWords(ic)` reads the words before the cursor FROM THE TEXT (same sentence, up to 6),
  `learnFinished` teaches `Words.learnPhrase(before, word)` (pair + every phrase of 2..5 words) after every finished word (`endWord`, `pickSug`, `writeNextWord`), and the guesses are looked up with `Words.nextAfterCtx(contextWords)`
  (`nextContext()` in `paintSugs`, the correction row and the GIF row). `Words.record` / `CHAIN` remain only as the fallback when an app will not give the text. PC test with the real tokenising: once = nothing; twice = "Ami " -> cheye,
  "Ami cheye " -> cheye / dekhi, "Ami cheye cheye " -> dekhi, "...dekhi " -> saradin; also after the cursor is moved into older text and after a restart (export / import). Not tested on a phone.
  Also seen in her screenshot: "Last word not corrected: “nit” → not: rare real word" (a rare dictionary word is only offered, by design).

- Version 124 (Tara sent only a screenshot of the Bolchi app on version 123, WhatsApp 23:21: "after “gach” nothing seen twice yet | memory: 94 of 136 pairs and 1 of 114 phrases seen 2+ times"). Reading: since the 23:06 screenshot (version 122) pairs went 133 -> 136 and phrases
  111 -> 114 while the "seen 2+" counts did not move at all: that is ONE short sentence typed ONCE (3 pairs + 3 phrases), i.e. the new text-based learning (version 123) works, and no sentence has been repeated yet on 123. To remove the guessing the Bolchi app now also
  shows "Learned lately (words before > word)": the last 8 words learned and the words the keyboard read before each (`noteLearned`, pref `learnLog`). Next step: ask her to type the SAME sentence twice on 124 in a chat box, then screenshot that block.

- Version 125 (Tara, 19 Nov, screenshot of the converter in WhatsApp with Hindi-script text, "To Bangla script" selected: "I have asked to translate in bengali but its giving Hindi"): "To Bangla script" (option 1) only converts words written in ENGLISH LETTERS (`Converter.romanToScript`,
  its regex sees Latin words only), so Hindi (Devanagari) text came back unchanged, and there was NO "Translate to Bengali" tab (only English and Hindi). Now: new tab "Translate to Bengali" (option 13, `CONV_NAMES`, first row {0, 7, 13, 1, 6, 2}) = `Converter.translateToBengali`
  (Google Translate auto -> bn; Banglish in English letters, which Google leaves unchanged, is only written in Bengali letters via `romanToScript`; the AI is the last fallback); and "To Bangla script" given Devanagari text now translates to Bengali instead of echoing it
  (`Converter.hasDevanagari`). Not tested on a phone (Google Translate cannot be reached from Claude's workspace). Her dictation screenshots also show the bolchi-keyboard.apk link and "All fixed _ memory & capital" sent to a contact: nothing to act on.

- Version 126 (Tara, 19 Nov 21:34, screenshot: "Rewrite it better" shows "⚠ AI service problem (503). Try again"; "my internet working fine but app is not working wherever net feature is required including speak"). 503 = Google's AI answered "busy" (so the phone WAS online);
  the keyboard asked ONE model (`gemini-flash-lite-latest`) and gave up. Now `Converter.gemini` tries `MODELS` in turn (flash-lite-latest, flash-latest, 2.5-flash-lite, 2.5-flash; each has its own free limit) on 503 / 500 / 502 / 504 / 429 (`Busy`) and 404, with a short pause, within 18 s;
  if all fail the message says "Google's AI is busy right now (code), every model was tried". Voice does not use Gemini (Android `SpeechRecognizer` + Google's voice service), so it cannot be fixed by the same change: `onError` now writes pref `voiceWhy`
  ("[HH:mm] network error (code 2), Bangla") and the Bolchi app shows it as "Last voice error"; the message on the space bar names the code (ERROR_SERVER 4 is no longer called "needs internet"). If Speak fails while the AI works, ask for that line:
  codes 1 / 2 / 11 = phone to Google network problem (try mobile data / Wi-Fi, Google app voice settings), 4 = Google's server, 9 = mic permission, 12 / 13 = language not installed. Not tested on a phone.

- Version 127 (Tara, 19 Nov 21:45-21:47, two screenshots on version 126: "Translate to English" -> "Google's AI is busy right now (503), every model was tried"; the keyboard on the বাং letter keys with the English chip dark; her words: "if I'm speaking and want it converted into Bengali word in Bengali
  letters then it's not happening; check the first screenshot, still not happening"). (1) "Translate to English" is `translateFree`: FREE Google Translate first, the AI only as fallback, so BOTH failed: Google Translate (translate.google.com / translate.googleapis.com, answer or error unknown) and
  Gemini (503 from all four models), while the phone is online and `inputtools` works: something between her phone and Google's translate / AI hosts (a Wi-Fi / DNS / VPN filter, a data-saver rule for the Bolchi app, or a real Google outage). Messages now carry the facts: Google Translate's HTTP code or exception and host,
  the AI's code and the first 140 characters of Google's answer (`lastDetail`), joined in one message. The Bolchi app "Test AI key" shows the full answer. Ask her for that result / the "Last voice error" line before changing anything else. (2) Speaking Bengali in Bengali LETTERS already existed but was hidden:
  select Bangla, then tap the Bangla chip AGAIN (`toggleVoiceScript`; the chip then reads বাংলা). It was not found (and with the English chip dark Speak listens in English). Now: tapping Bangla / Hindi shows on the space bar what Speak will write and how to switch;
  the Bolchi app has two buttons "Speak on Bangla / Hindi writes: Banglish | বাংলা letters"; the keyboard re-reads the choice when a box opens. Not tested on a phone.
  Also in 127 (Tara: "do not push, it is consuming my token; fix and then push; you can add some more API, I will generate some free keys"): ONE release for all of it. Backup AI services, used only when Google's AI cannot answer (busy, key refused, unreachable) and only if she saved a key
  in the Bolchi app ("Backup AI keys"): Groq (`api.groq.com/openai/v1`, llama-3.3-70b-versatile then llama-3.1-8b-instant, prefs `groqKey`) and OpenRouter (free models `:free`, pref `openrouterKey`), through `Converter.chatCompat`; `Converter.useExtraKeys(prefs)`. Translating also
  ends with MyMemory (`api.mymemory.translated.net`, no key, ~500 letters per request, daily limit, source language from the letters) after Google Translate and the AI. Network errors (`IOException`) skip the other Gemini models at once and go to the backups. The messages list every step that failed.
  Do not release more builds than needed: she pays for the tokens of every build cycle (fix first, one build, one release).

- Version 128 (Tara, 19 Nov 22:09, screenshot on version 127 with both backup keys saved: "saved both keys but it's not working"; Translate to English shows "Google Translate didn't answer (429 from translate.googleapis.com). AI: Could not reach Google's AI (SocketTimeoutException). Groq: answered 404. OpenRouter: answered 404 ..."):
  from HER network: Google Translate = 429 (the free `translate_a/single?client=gtx` endpoint refuses her IP: too many requests), Gemini = socket timeout (so the earlier 503 was also this path), and Groq / OpenRouter = 404 for BOTH models each.
  A 404 from both most likely means the model names I wrote from memory are gone (it is Nov 2026; names change) or a filter in front of those hosts. Fixed what can be fixed: `Converter.discover` asks each service for its CURRENT model list (`/openai/v1/models` for Groq,
  `/api/v1/models` for OpenRouter, only free ones there; speech / guard models skipped; preferred names first; kept 6 hours); the old fixed names are only the fallback. `chatCompat` now reports the service's own error text ("404 model_not_found ..."), messages are one short phrase per step
  ("Gemini: no connection (SocketTimeout). Groq: 404 ... OpenRouter: ..."), Gemini's read timeout is 14 s (was 20) so the backups still get time. NEW in the Bolchi app: "Test all connections" (`Converter.testAll`): Google Translate, MyMemory, inputtools, Gemini, Groq (chosen model + its answer), OpenRouter,
  each with milliseconds and the exact error. If most of them fail on Wi-Fi, ask her to try the same button on MOBILE DATA (office / home Wi-Fi filters, a VPN, Private DNS or a data-saver rule for Bolchi can block AI and translation hosts while browsing works). Not tested on a phone.

- Version 129 (Tara, 19 Nov 22:37-22:38, two "Test all connections" results on version 128, Wi-Fi and 5G): Google Translate ✗ 429 on BOTH (the free `client=gtx` door is shut for her: it is not her network); MyMemory ✓ (but "hello" -> "ami tomake chai": poor, last resort only); inputtools ✓;
  Gemini ✗ timeout 20 s on one run and ✓ but 18 s for the single word "ok" on the other (flash-lite-latest is far too slow for her); Groq ✓ (picked `openai/gpt-oss-120b` from the live model list: 0.85 s on 5G, 6.5 s on Wi-Fi); OpenRouter ✗ 429 / timeout (free models are crowded). Also in the same screenshots:
  "Last voice error: client error (code 5), English, not listening" = ERROR_CLIENT after we stopped, harmless (no network voice error was recorded); the learning works (pairs 219 of 1037, phrases 194 of 2407 seen 2+ times; "Learned lately" shows what was read).
  Changes: (1) `Converter.gemini` tries GROQ FIRST when a Groq key is saved (`viaGroq`), then the Gemini models, then OpenRouter (`viaOpenRouter`): this also speeds up the voice AI tidy, grammar, tone, replies and translating (the AI step). (2) After a 429 from Google Translate it is not asked again for 15 minutes
  (`googleTranslateBlockedUntil`), so Translate goes straight to the AI (2 s wasted once, not every time); the test button asks it again. Speak: she has not said what happens when she taps it; if it still fails ask for the exact message on the space bar and the "Last voice error" line.

- Version 130 (Tara, 19 Nov: "why is Google Translate / Gemini suddenly failing? add as many open source / free services as possible, something completely free that never fails; Groq may fail too"). WHY: Google Translate's `client=gtx` is an UNOFFICIAL free door that Google
  closes per IP address (429; phone networks share one public IP among very many users) and the free Gemini tier is shared capacity (503 / timeouts / 18 s answers): neither is a fault of her phone and either can recur. No online free service can promise "never fails", so:
  (1) the only thing that cannot fail that way is the translator ON the phone: `Offline.java` (ML Kit on-device translation, `com.google.mlkit:translate:17.0.3` in app/build.gradle; English / Hindi / Bengali, ~30 MB each, downloaded once from a button in the Bolchi app "Download offline translator";
  needs Google Play services). It is the step after the AI services in `translateChain` (translators with no key -> AI chain -> on-phone -> MyMemory) and fails fast when the languages are not downloaded. (2) Several keyless translators in turn (`translateDoors`): Google gtx, Microsoft Edge's translator
  (token from edge.microsoft.com/translate/auth), Google's Chrome-extension door (clients5.google.com), Lingva instances (open-source Google front ends). (3) The AI chain (`Converter.gemini`) now has providers as data (`Prov`): FAST Groq, Cerebras, Mistral (keys optional, boxes in the Bolchi app, model lists asked live),
  then Gemini, then OpenRouter (free models), LLM7 and Pollinations (NO key needed). (4) A service that failed is left alone for 3-15 minutes (`down` / `mark`, `clearDown` in the test) so a dead door costs a few seconds once, not every time. "Test all connections" now lists every door and provider.
  The AI-only features (grammar, rewrite, tone, replies, voice AI fix) cannot be offline: only the many online providers protect them. Not tested on a phone; the first build with the ML Kit dependency (APK is larger) must compile on GitHub.

- Version 116 (Tara, 17 Nov: "check everything, whether each build is properly done; flag any issue and correct it; one go; final version"). AUDIT of versions 71-115:
  `BolchiIME.java` read end to end, the pure-Java parts tested on a PC. NOTHING here is seen on her phone. Faults found and fixed:
  - The row that points at a doubtful word after speaking (version 104) was wiped the moment it appeared: the dictation's own text update reached `onUpdateSelection` as
    "she is typing" and cleared it. That is why she "could not understand how to use it". Now only typing / deleting / a pick / "keep" / a new box end it (`clearRepair`).
    A one-step cursor move recomputes the tap row (its saved `repairPre` / `repairSuf` would have replaced the wrong letters).
  - The same row flagged about one ordinary Bengali word per sentence ("tarpor ? trooper", "matha ? martha": 20 of 173 words in a PC sample), which would have shown
    once the row stayed. `findSuspects` now needs a real sign: the phone also heard an English dictionary word there, or `Words.soundAlike` (strict) matches;
    words with a Bengali ending on a known word are skipped (`desiWithEnding`). The tap-into-a-word row is unchanged (loose matches, her own choice).
  - Voice full stop: `endSentence` wrote the mark wherever the cursor stood, so tapping into a word within 3 s of a pause put a full stop INSIDE that word. It now
    writes only right after the sentence just spoken (`stopTail`) and never before a letter. The "take the full stop away again" step (`newLead`) only removes the mark
    the timer wrote, at that place (`autoDotTail`). AI tidy arriving after the timer's mark no longer gives two marks ("acho??"). A round that ends without a final
    text keeps `spokenAll` in step.
  - Voice converter: "কাজটা" came out as "costa" (the ট of the ending -টা counted as an English sign). `Translit.endingOnly` skips the sound match for short words
    whose only sign is the ending -টা / -টি / -টাকে (master, university, computer, party still convert). Still wrong, rare: ফানুস → phones, টাঙা → tongue, ফরমান → freeman.
  - Numbers: a dot or comma right after a digit stays inside the number ("3.5", "10.30", "1,500"; it used to come out "3. 5", so a sum with a decimal could not be
    typed). Side effect: a sentence that ends on a number gets no automatic space after its full stop.
  - Calculator: "200+15%" gave 200.15; now like a pocket calculator (230; "500-10%" = 450). "200*15%" is still 30.
  - Two spaces: after tapping a next-word guess, emoji, name, date, number, money, swipe guess or a Bangla / Hindi candidate, the space key added a second space
    (`pickedSpace` was only set for the main word slots).
  - English voice: "no no" is no longer a correction phrase ("I am going no no problem at all" lost its words).
  - Emoji search: switched the typing layout to English for good (now `emoBackLayout` brings her layout back) and swallowed letters in a password box (now not started there).
  - Small: backspace-undo of a correction left the old choices in the row; `setLayoutTo` deleted the wrong length in font mode; a repair offer survived into the next box.
  - Dead code removed: `SAVE_TOKEN`, `repairSaveWord`, `slotRepair`, `typeStyleApp`. `Words.SHORT` is a proper subset of `FIXED` (checked: 59 of 59), left as it is.
  Checked and found in order (PC): her test sentence corrects fully; 0 Banglish words changed in a sample; dedupe / dropRepeat cases ("Kota hoy beauty", Bengali repeat);
  markFor; emoji search; money / units with made-up rates; long sums; 170 themes parse, names unique, none with unreadable letters; fonts round-trip (4 one-case fonts lose capitals, by nature).
  Read and found no fault, but NOT provable without a phone: touch lift, GIF send, AI replies, SMS code, see-through themes, tap-to-repair in apps without setSelection.
  How the PC tests were run: javac on Words, Spoken, Translit, HindiTranslit, Calc, EmojiHints, FontStyles, Swipe, Converter, Phonetic, Themes with tiny stand-in
  classes for `org.json` and `android.graphics.Color`; Maven and the Android SDK are blocked, so `BolchiIME` itself is only compiled by GitHub.
  Still open (need her phone): the three screenshots (Bolchi app screen; a dictation result; the keyboard after typing "Ma ke niye"); "stoppedAnd" / "removing.fter"
  (lost space / letter at a voice round boundary, seen once on version 90, cause not found).

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
