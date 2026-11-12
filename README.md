# Bolchi Keyboard

An Android voice keyboard. Speak Bengali and it types Banglish (Bengali in English letters,
like "ami bolchi") straight into WhatsApp, SMS or any other app.

## Get the app on your phone

1. Put this whole folder in a GitHub repository (branch `main`).
2. GitHub builds the app by itself: open the repo's **Actions** tab and wait for the green tick (about 3 to 5 minutes).
3. Open the repo's **Releases** section and download `bolchi-keyboard.apk` on your phone.
4. Tap the downloaded file to install. Android will ask you to allow installs from Chrome or My Files; allow it.
5. Open **Bolchi Keyboard** and follow the four steps on screen (allow mic, turn the keyboard on, switch to it, try it).

## Using it

- In any app, tap a text box, switch to Bolchi, tap the mic and speak Bengali.
- **ABC** goes back to your normal keyboard (hold it to see the keyboard list).
- The row also has comma, full stop, question mark, space, delete and enter.

## Good to know

- It uses the phone's own voice typing (the Google app), so it needs internet and Bengali (India) added there:
  Google app > Settings > Voice > Languages.
- Spelling follows sound rules plus a list of common words in
  `app/src/main/java/com/tara/bolchi/Translit.java`. Add your own words to the `DICT` list there.
- Each build is signed with a temporary key. To install a newer build, uninstall the old one first.
