# Changelog

## v1.9.70 (2026-11-19)

- Say when your phone's own switch is what keeps the keyboard quiet. Out of the box the keyboard follows your phone's touch sounds and touch vibration (Touch interactions on a Samsung), not the volume. With those off it made no sound and no vibration even with every option on Sounds & Vibration turned on, and nothing said why. That screen now shows "Play key sounds anyway" or "Vibrate on key presses anyway" while it's happening, and one tap sets the keyboard to play or vibrate regardless.
- Update the encryption library behind clipboard history and your personal dictionary to SQLCipher 4.20.0. It brings SQLite 3.54.1, which fixes two SQLite security bugs (CVE-2026-119375 and CVE-2026-119377). History and words saved by older versions open as before.
- Make "Always vibrate, regardless of system settings" really vibrate. On Android 13 and later the phone threw those vibrations away whenever its touch vibration was off, so picking that option changed nothing. The previews on the vibration length and strength sliders follow the same choice now.
- Put 👍 first when you type ":good". Every emoji that lists "good" as a keyword scored the same, and ties kept the emoji panel's order, so the strip showed 🫡 😪 😴. Emoji that tie now go by how often people use them (Unicode's own usage counts), so ":good" gives 👍 👏 😴, in your skin tone where there is one.
- Open the language list in the keyboard language editor on a normal tap. The "Primary language" field only opened when your finger stayed down long enough, so quick taps often did nothing, and TalkBack's double-tap and Switch Access couldn't open it at all. Screen readers now hear one dropdown with the language in it, and Tab or the D-pad stops on it once, where Enter opens it. When you save without picking a language, the field still gets its red outline, and TalkBack now says so there too.
- Even out the spacing on the settings home screen. The search box, the setup card and the Active badge used a few odd sizes of their own. They now follow the same spacing steps as every other settings page.
- Remove two unused honeycomb key widgets with hard-coded dark colors. The Honeycomb layout you can pick for a language never used them, so it looks and works the same.
- Say what each suggestion engine does in the keyboard language editor. The engine names were English words written into the screen, so they never got translated. They're translatable now, and a line under the list says whether the engine you picked handles word suggestions, spell checking or both. A language saved with an engine this version can't use says "Unavailable" instead of looking like nothing was picked, and that includes a missing spell checker behind a working suggestion engine. A spell checker that was never picked shows the empty choice marked as missing, so you can see which field to fill before saving.
- Stop unpacking a backup, theme or extension file once it unpacks past 250 MB in total. Files left out for being too big didn't count toward that limit, so a crafted file could keep the keyboard unpacking far more than that. A failed unpack also removes everything it had already written instead of leaving half a copy behind.
- Say why a shared extension file can't be opened. Sharing a theme or extension to the app from another app opened the import screen on "No files selected" whenever the file couldn't be read, was too big or the other app wouldn't hand it over. The import screen now shows what went wrong, with Retry to read it again.
- Ask for the passphrase again after a wrong one, without picking the file again. Restoring an encrypted backup or importing an encrypted dictionary used to throw the file away on a typo, so you had to find it in the file picker all over. The passphrase box now stays on the same file, says what went wrong right under the field and puts the cursor back in it, and TalkBack reads the error out. Cancel still clears the copy.
- Explain why a theme can't be opened for editing instead of printing a stack trace. If its file went missing, got damaged or can't be edited, the editor used to fill the screen with the raw error, file paths included. It now says which of those happened in plain words, with Retry and Back.
- Find languages when you type them without accents. Searching the language list for "francais" or "espanol" missed Français and Español, because it only lowercased what you typed. It now ignores accents the way settings search already did. Letters like ł, ø, ß and ə match a plain l, o, ss and e in both searches and in the language picker during setup, and language codes like pt-BR still work. TalkBack also reads how many languages matched (or that none did) once you pause typing.
- Translate the names, tooltips and short key labels of the Translate, Add task, Insert event and typed-text quick actions. They were written into the app in English, so every language saw them and TalkBack read them in English. TalkBack now reads each action's full name, never the short "Task" or "Cal" on the key. Forward delete also gets a real tooltip instead of an error placeholder. A typed-text key with no text shows a question mark instead of the word "null", and one whose text starts with an emoji shows the whole emoji instead of half of one.
- Say when a copy didn't work on the About, Privacy audit and Sync screens. Copying the audit log claimed success even when the clipboard refused it, copying the signing fingerprint said nothing when it failed, and a failed copy of the pairing payload could close the Sync screen. Each copy button now shows one message saying whether the text was copied, and a copy that fails stays out of the keyboard's clipboard history, so you can't paste it from there either.
- Close the suggestion bar's benchmark timing even when drawing the bar fails. A failed redraw used to leave that timing open in the trace. Nothing changes while you type.
- Follow Remove animations the moment you turn it on. Android keeps that switch somewhere that doesn't restart open screens, so with the app already open the settings pages kept sliding and the Aurora theme's bands kept moving until you left and came back. Both stop right away now, and start again when you turn it back off.
- Mirror the settings page transitions in right-to-left languages. In Arabic, Hebrew, Persian or Urdu the next page still slid in from the right and the page you left still shrank toward the right edge, the way it does in English. Both now run the other way.
- Keep the preview Obtainium link from offering the stable release. Its file filter also matched every regular release APK, so with no preview out it would have handed a preview subscription the stable build. It now only takes files named like `SwiftFloris-v1.9.70-beta.apk`, which no regular release uses.
- Build the app without Kotlin's experimental local type aliases. One shorthand name in the keyboard's screen-size code needed them, and they made the compiler mark every class as a pre-release build. Nothing changes in how the keyboard sizes itself.
- Check the release build's screenshots before every release again. That check couldn't even compile, since one shared test used a class only debug builds have, so text and settings that only release builds carry never got a visual check. It builds again, keeps its own reference pictures for the screens that show the app's name, and the release checklist now runs it.
- Measure what the English next-word model costs on a real phone. On a Galaxy S22 Ultra, loading the 5.9 MB file adds 5.6 to 5.9 MB of file and code pages, which aren't app memory, and 0.2 to 0.3 MB to the Java and native heaps. It loads in about 6 ms and each lookup takes a few microseconds. The numbers are checked in with the other benchmark results.
- Show the emoji key again as soon as you switch the utility key back to emojis. Going from Switch language to Switch to emojis left an empty gap beside the comma until the app restarted, because a key that only moved over kept being drawn in its old spot.
- Keep fast typing on a hardware keyboard in order right after you tap in the text. With a layout imported for that keyboard, a space in a quick burst (a text expander or a macro, say) could land one letter early and turn "river violet" into "rive rviolet".
- Let settings titles and summaries wrap when your phone uses large text. Above 130 percent text size, rows showed "Display language na..." and cut every summary at two lines, so you couldn't tell what a switch did. They now show in full. At normal sizes they look the same as before.
- Show what the two-value sliders are set to again. Font size multiplier, Keyboard height, Key spacing factor, the clipboard's Number of grid columns and the emoji history's Maximum items to keep showed only their name, so you had to open each one to see its two values. The rows show both again, like "100% / 100%".
- Read each bundled extension folder once per run. Telling folders from files, the extension index listed every bundled folder again for each kind of extension, every time the app started. Under the test suite that kept every background thread busy long enough for unrelated tests to time out.

## v1.9.69 (2026-11-19)

- Correct more slips on space. A wrong letter that happens to spell some rare entry in the word list, like "thos" for "this", "rhe" for "the" or "yoy" for "you", counted as a real word and stayed as typed. Now space fixes it when a far more common word is one letter off. Real words that are just rare, like "snide", "plop" or "wager", stay as typed, and so does any word you've typed before. Because every word in the list used to be learned the first time you typed it, a slip like "thos" that space let through was learned and then never fixed. Those learned slips are cleared once on update, and a slip now has to be typed twice before it's learned, the same as any word the keyboard doesn't know. Undoing a fix still teaches the word right away. Short slips also stop turning into rare longer words, so "thw" now becomes "the" instead of "thwart". When several fixes are common enough, the keyboard now weighs where your taps landed between all of them instead of always taking the most common one. On the accuracy test careful typing gets 60 of its 101 slips fixed instead of 52, sloppy typing 434 of 840 instead of 378, and still no correctly typed word gets changed.
- Keep words the keyboard learns while your personal dictionary can't be opened. If the encrypted store wasn't ready, a new word was only remembered until the keyboard restarted. It now waits and gets saved as soon as the store opens.
- Suggest the word you usually type next while you're still typing it. After "see you", typing "to" now puts "tomorrow" first if that's how you finish the phrase, even though plenty of more common words start with "to". It kicks in from the second letter, once you've typed the phrase twice.
- Rank the words you use most above the ones you've typed once or twice. Every learned word reached the top weight on its second use and tied with all the others, so the shortest one won. Now each of a word's first 11 uses moves it up. Words only you use, like names or slang, also get full credit when suggestions are sorted and when you glide. They used to score there as if they weren't words at all. The keyboard keeps up to 10,000 of your words per language instead of about 4,000.
- Remember phrases longer, and keep the new ones. Each word kept only its 16 most-typed followers, so a pair you'd typed once was usually thrown out the next time the keyboard closed, before you could type it the second time it takes to be suggested. New and recently used pairs now win that cut, and there's room for twice as many. A pair you've typed twice also stays suggestible for a year without use instead of fading after about three weeks, and phrases that start with "I" or "a" ("I think", "a lot") are learned at all now.
- Learn the word you type right before punctuation or Enter. Only the space key taught the keyboard a word, so the last word of a sentence ("see you tomorrow.") or of a message was never remembered or paired with the words before it. Next-word suggestions also stop carrying over from the previous sentence. After "Thanks. " you get sentence starters, not words that tend to follow "thanks".
- Make settings a short list you open in place. The home screen is now a search box and one row per area (Keyboard, Typing, Smartbar, Sounds & Vibration and so on). Tap a row and its settings open right under it, and opening another row closes the first. Localization, Sync, Backup, Restore and Extensions still open their own page because they have buttons of their own along the bottom. Each section inside a page now sits in its own rounded card under a small heading, so long pages are easier to scan.
- Cut the promotional copy from settings and setup. The "No cloud", "No telemetry" and "Offline" chips are gone, along with the privacy promise cards on the home, AI features, clipboard, media, privacy and setup screens. The warning that shows up if a build ever asks for Internet access stays.
- Shrink a long suggestion to fit its spot in the suggestion bar instead of cutting it off. Words used to trail off in "..." at the theme's text size whenever they ran wider than their third of the bar. Now a word that doesn't fit gets smaller, down to half the normal size, and only a word too long even for that still ends in "...". Short words keep the normal size. Clipboard suggestions stay as they were, since a copied paragraph won't fit a spot at any size.

## v1.9.68 (2026-11-16)

- Fix the per-app "Suggestions: Off for this app" setting. It only stopped the keyboard from underlining the word you were typing, so that app still showed suggestions and the space bar still corrected words. It now works like turning "Display suggestions" off while that app is open. Emoji and clipboard suggestions still follow their own settings.
- Fix autocorrect from a physical keyboard in Chrome. Typing "thier " on a web page could leave "thiertheir ". With the on-screen keyboard hidden, the page kept the settings of the field before it (often the address bar, which turns suggestions off), so the fix landed after the word instead of replacing it. Every field now gets its own suggestion, privacy and password settings the moment it's focused, keyboard shown or not, and a correction always replaces the word it was made for.
- Keep a fast burst of keys from a physical keyboard tracked when it presses Right, Home, End, Tab or a numpad Enter, or pastes. With no keyboard layout imported, every key typed after one of those read as a jump, so the next word could go in uncorrected or get corrected against text the app hadn't caught up on. The keyboard now works out where Right leaves the cursor from the text next to it, and where Home and End do in a one-line field. A numpad Enter that types a line break counts as one typed char, like the main Enter. After a Tab, a paste, Up or Down, or a shortcut like Ctrl+Z or Ctrl+A, it waits for the app to say where the cursor went and carries on from there.
- Keep a fast burst of keys from a physical keyboard tracked when it presses Left in Hebrew or Arabic text. Fields don't agree on which way Left goes there. A classic text box follows the text, so Left moves forward through Hebrew and nowhere at its end. Newer app screens and most web pages run left to right in an English app whatever you type, so Left steps back, and a page set to right to left moves forward even through English. The keyboard always expected a step back, so it lost track of the keys typed after it. Now it follows both ways until the field's first move shows which one it uses.
- Keep a fast burst of keys from a physical keyboard tracked when Backspace deletes a Chinese or Japanese character that carries a variation selector (an invisible mark that picks one of the character's variant shapes). Most app fields and Chrome delete the character and its mark together, but the keyboard expected only the mark to go, so it lost track of the keys typed after it. Fields that delete just the mark still work, right up to the start of the text, and the keyboard learns which way a field does it from that Backspace.
- Keep a fast burst of keys from a physical keyboard tracked when it types an accent mark on its own key and then moves Left. The keyboard counted the mark as a char of its own, but text fields move over a letter and its accent together, so the keys after the Left read as a jump. The same went for a conjunct typed key by key in Hindi and similar scripts. The keyboard now notes which char each key typed and steps back the way the field does.
- Keep a fast burst of keys from a physical keyboard tracked after a Left arrow over a conjunct (two consonants joined by a virama, like क्त in Hindi). Chrome and most newer app fields move over the whole conjunct, while older fields stop in the middle of it, and the keyboard only knew the second kind. In the first kind, the keys after the Left read as a jump and a word typed right after could be corrected against old text. The keyboard now expects either landing point and learns from the app's first report which kind of field it's in. That covers the consonants Unicode added later too, the ones for Sindhi and other languages and Gujarati's ZA.
- Keep a space the keyboard corrects in its place during a fast burst of keys from a physical keyboard. When the keyboard had caught up right at the space, it sent the space and its fix to the app on a different path than the key itself, so the app could type the letters after the space first. "wiht" followed quickly by " river " came out "withriv er" or "with er riv" in 9 of 16 tries. The keyboard now waits for the app to take the space before it lets the next key through, and does the same for an Enter it handles.
- Keep track of a fast physical-keyboard burst that moves Left over a letter typed with a separate accent mark. Text fields move over the letter and its accent in one step, however many accents are stacked on it, but the keyboard expected one char, so it lost track of the keys typed after the Left arrow. The next word could then go in uncorrected, or be corrected against text the app hadn't caught up on.
- Correct the next word right away after a fast physical-keyboard burst that deletes a char and types it again, like a text expander fixing a typo. When the app reported the whole burst at once, the keyboard kept waiting for keys it had already handled, and a space typed in the next 2 seconds went in uncorrected. A burst of more than 1,024 keys before the app caught up now waits for it, instead of correcting against text the app hadn't caught up on.
- Keep up with a fast burst of keys from a physical keyboard when it includes Backspace or the Left arrow. With no keyboard layout imported, every key typed after one of those read as a cursor jump, so a space later in the same burst could be corrected against text the keyboard hadn't caught up on. A text expander that deletes its abbreviation before typing the full text does exactly this. A burst of more than 32 keys before the app caught up had the same problem.
- Keep a line break from a physical keyboard in its place during a fast burst of keys. With no keyboard layout imported, the letters a macro, text expander or barcode scanner typed right after Enter could land in front of the line break. "hedgehog", Enter, "river" came out as "hedgehogriv" with "er" on the next line.
- Add screenshot checks for the Languages and Language packs settings screens, laid out right to left and with text at 200 percent, so a layout that breaks there shows up before a release. The screenshot check also runs again now when only a saved screenshot changed. Before, Gradle skipped it and nothing was compared.
- Say which language packs came with the app and which you imported. Each row on the Language packs screen now starts with "Built-in" or "Imported", so you don't have to guess from whether there's a Delete button.
- Keep a fast burst of keys from a physical keyboard intact. With no keyboard layout imported, the app types the letters itself and tells the keyboard afterwards, so a macro, text expander or barcode scanner could press space before the keyboard knew the whole word. Space then "fixed" the cut-off word: "river violet hedgehog river " came out as "river violet hedgehogs river og". Now a space that arrives that early is typed by the app too, right after the letters, and the word is left as typed. At normal typing speed, space still corrects.
- Make English autocorrect look at the words before the one you're typing. A model of which words tend to follow which, counted from 127 million words of English web text, now helps rank the fixes, so a slip gets the word that fits the sentence rather than just the most common lookalike. When a real word doesn't fit, like "form" in "I got a letter form my mom", the word that does now shows up in the suggestions, though space still leaves what you typed. On the accuracy test careful typing now gets 52 of its 101 slips fixed instead of 50 and sloppy typing 378 of 840 instead of 344. The word you meant lands in the first three suggestions more often, and still no correctly typed word gets changed. The model is read in place from the app file rather than copied into memory, and it loads in the background. The app is about 6 MB bigger. If the model file is ever damaged, it's turned away when it loads and autocorrect carries on without it.
- Add an autocorrect accuracy test. It types 400 everyday English sentences with simulated taps that land off-center the way a thumb does, starts each sentence with shift on, and sends each word through the same suggestion, ranking and commit steps the keyboard uses when you press space or type a period or comma. It counts the slips that get fixed, left alone or turned into a different word, plus any correctly typed word that gets changed. Right now careful typing gets about half its slips fixed and sloppy typing 41%, no correctly typed word is changed, and all six common misspellings in the test (adn, hte, teh, recieve, taht, wiht) are fixed. It also counts correctly typed words that get some other word offered in the suggestions, about 1 in 80 right now. An autocorrect change now has a number to beat.
- Update emoji to Unicode 18.0 and CLDR 49. The nine new emoji, among them a cracking face, a pickle, a lighthouse and left and right thumb signs in every skin tone, show up in the panel once your phone's emoji font can draw them. Emoji names and search words now ship for 49 languages instead of six, so the emoji panel's search and `:` shortcodes work in Arabic, Hebrew, Japanese, Polish, Russian and most other keyboard languages. Esperanto, Central Kurdish and the few others CLDR doesn't cover keep searching the English names. The keycap #️⃣ was missing from the panel because its line was read as a comment, and it's back. English search also couldn't find 14 emoji, among them 🔣 and the Antigua & Barbuda flag, and Spanish missed 25, Portuguese 15 and German 2. The old data files kept a & or a quote in a name as markup, which split the line apart. The app is about 3 MB bigger. Part of issue #24.
- Fix misspellings to the British spelling on the English (UK) and English (Australia) keyboards and other Commonwealth English. "jewelery" turned into "jewelry" and "nieghbor" into "neighbor" there. Now they become "jewellery" and "neighbour", and so do "favorate", "liscense", "manuever", "recogize", "relize" and a few more. The English (Canada) keyboard gets "neighbour", "jewellery" and "licence" the British way but keeps "realize", "recognize" and "fulfill", the way Canadians write them. US English keeps the US fix. Seven typos of British spellings, among them "nieghbour" and "recogise", are on the list now too, and a US keyboard turns "recogise" into "recognize".
- Ask for your keyboard language during setup. A new install used to start on English (US) QWERTY whatever language the phone was set to. Setup now suggests the keyboard for each of your phone's languages, keeps English one tap away (it's the main choice when there's no keyboard for your language yet) and has a searchable list of every bundled language with its layout's name, which finds German as "Deutsch", "German" or the name your phone's language gives it. On a Chinese phone it says Zhengma is the only Chinese keyboard so far and where to find it. Picking another one later from the same step swaps it rather than adding a second keyboard.
- Make resizing the keyboard smoother. Each frame of a drag laid out every key again, and the keyboard's window asked Android for a new size on every frame too, so the keyboard stuttered behind your finger. Now it stretches a picture of itself while you drag and lays the keys out once when you let go. A screen that runs at both 60 and 120 Hz also kept switching between the two during the drag, and each switch held up the next few frames. The keyboard now keeps the screen at its fastest rate until you let go. On a Galaxy S22 the share of late frames during a drag went from over half to under 6 percent.
- Fix the "Don't learn" button that's meant to show up when the keyboard learns a new word. The suggestions that came in right after it replaced it before it was ever drawn. Now it shows up in the suggestion bar once a word you typed twice gets learned, showing the word with "Don't learn" in small print under it, and goes away when you start the next word or move the cursor. Words a dictionary already knows don't get it.
- Learn a new word only once you've typed it a second time. Until now one typo that slipped past autocorrect was learned on the spot, then offered as a suggestion and never corrected again. Picking a word from the suggestions or undoing a correction still teaches it right away. So does backspacing over a correction and typing your word again.
- Fix common misspellings like "teh", "recieve", "untill" and "thier" when you press space. The word lists had picked up 82 of them, some ranked like everyday words, and autocorrect leaves a word alone once it thinks it's real. They're out of the lists now, and a set of about 780 common misspellings gets its fix right away. Real words that look like typos, like "wether" and "calender", stay as typed, and so does any word you've taught the keyboard. If an older version learned one of these misspellings from your typing back when it counted as a real word, the keyboard forgets it once after the update so the fix works for you too.
- Fix settings that stopped saving after one was changed right as the screen or keyboard changing it closed. That cancelled save took down the part of the app that writes settings to storage, so every later change either never finished or was gone after the keyboard restarted.
- Fix settings that could stop saving when one was changed while the app was still reading them, which it does each time the keyboard starts and when you restore a backup. The change and the reading each waited for the other, so neither finished and no setting saved again until the keyboard restarted.
- Learn which words you type together again. The keyboard mistook its own typing for a tap somewhere else in the text, so after nearly every word it forgot the one before, and next-word suggestions almost never picked up your own word pairs. That goes for a hardware keyboard without an imported layout too. Tapping elsewhere in the text, selecting text or switching fields still starts fresh, and so does coming back to the same chat from another app.
- Fix the keyboard sometimes opening with only the suggestion bar and no keys. When Android had closed the keyboard and it started fresh, it could look for its built-in layouts a moment before they were ready, and it kept showing that empty result until it restarted again.
- Stop space from changing a word into a suggestion meant for another one. Hit space right after the last letter and the suggestions on screen could still be the ones for a few letters earlier, so "river" turned into "riverbed". Typed fast enough, the next word could even get the previous word's suggestion. Space now only uses suggestions made for the word you're on and leaves it as typed otherwise.
- Stop common words from counting as misspelled for the rest of a session. Each letter you type cancels the work started for the letter before, and a cancelled check of how common a word is got saved as "not a word" until the keyboard restarted. That word then lost the middle of the suggestion row, where space could swap it for a longer one.
- Add `tools/check-memory-limiter-recovery.ps1`, which has Android 17's memory limiter close the keyboard on purpose and checks it comes back with your learned words, suggests from them again and reports the closing as a memory kill.
- Fix the benchmark scripts in `tools/`, which still looked for the benchmark app under its name from before v1.9.0 and couldn't find it.
- Add a memory benchmark for the keyboard's heaviest work: first render, loading the dictionary, glide typing, emoji search, a full clipboard history and a full sticker cache. `tools/benchmark-ime-memory.ps1` runs each one in a fresh process and records memory before, after, and after the app gives memory back.
- Learn the words you type on a physical keyboard, with or without an imported layout. Its space bar only taught the keyboard a word when autocorrect had changed it, so suggestions never picked up the words you typed yourself. A word autocorrect fixes is also learned once instead of twice, which had taught suggestions odd pairs like "florida florida".
- Keep the words you typed last in next-word learning when Android closes the keyboard. Learned word pairs were saved every 20 words, so a keyboard closed in the background, which Android 17 does to apps over their memory budget, forgot up to 19 of them. They're now saved each time the keyboard hides, including a word you finish right as it hides, and a save that fails is tried again next time.
- Give memory back sooner when the keyboard is off screen. Cached sticker images, per-app colors, smart compose and translation results are now let go as soon as the keyboard hides, and cached word lookups and glide data follow once the app is in the background. Before, only sticker images were freed, and only in the background. Everything fills back in the next time it's needed. Sticker images also stay under 24 MB in all now. Big stickers were kept at up to 1,023 pixels a side, so a full set could take over 250 MB.
- Tell a memory kill apart from a crash. Android 17 can close an app that goes over its memory budget even while you're typing, and that looks just like the keyboard crashing. The keyboard now notes how it last closed (memory kill, crash, not responding and so on), and the problem report in About and the debug log say which it was. Only the kind of exit, its time and memory figures are kept, never anything you typed.
- Fix the benchmark build, which has failed its permission check since August. The check allowed the receiver permission every Android app gets under the release and debug names but not under the benchmark build's own name.

## v1.9.67 (2026-11-15)

- Stop turning away a Tasker action that arrives while the keyboard is starting up. If the action was what started the app, it got there before your settings had loaded and was refused as if Tasker automation were off. It now waits a few seconds for them.
- Keep Touch calibration, Scramble numeric passwords, CJK mixed-script spacing and Tasker automation the way you set them. Each one looked changed but went back to its default whenever the keyboard restarted, and so did the setting that shows only the Smartbar while a hardware keyboard is connected.
- Switch to the right keyboard language when a text field or app says which language it's in. It only picks from the languages you've already added and never downloads anything. A field that doesn't say, or that fits more than one of your languages, goes back to yours. A language you picked yourself in that app, or one remembered for it, still comes first. You can turn it off with Match the app's language under Remember language per app.
- Keep both imports' stickers when two run at once. Sharing stickers while another import was still saving could drop one import's stickers from the pack, while their image files stayed on the phone. Clearing the pack now waits for an import that's saving, and a restore can't mix with an import either. Backups and restores also leave out the files an unfinished import is still holding, which could otherwise end up stuck in the pack folder.
- Keep "Reset trust" for add-ons from being undone at startup. If you reset trust or rescanned add-ons while the keyboard was still checking them after starting, that older check finished afterwards and brought the old trust back. A startup check that fails also leaves alone a list you refreshed in Settings meanwhile, and trusting a changed certificate is saved right away, even if you leave before the rescan finishes.
- Check a saved backup before calling it done. Saving a backup to a file said it worked as soon as the write finished, even if the storage app cut it short or changed it. The backup is now opened the way restore opens it, settings included, then the saved copy is read back and has to match byte for byte. Afterwards the screen shows what you got: format version, encryption, which sections are in it, which ones you picked that had nothing to save yet, what isn't included, the exact size and the SHA-256 checksum. If the check fails it tells you why, and as long as you stay on the screen, tapping Back up again saves the backup it already built (or builds it again if that file changed since). A backup with a file restore would leave out, like one past restore's size limit for a single file, fails the check instead of restoring without it. A phone that runs out of space during the check now hears exactly that instead of being told the backup is damaged. A backup bigger than the 256 MB restore accepts is stopped before it's encrypted, with a note on what to leave out. Leaving the screen while a save is finishing no longer reports it as failed. Turning the phone mid-save still shows how it went, and keeps the sections you picked, where you were saving it and the details of the last save. A file that already has something in it isn't written over (save under a new name instead), and a new file that didn't check out is deleted, so a failed save never leaves a broken backup behind. Scheduled backups use the same check, read the file back again after renaming it, only count backups that would restore when deciding which old ones to delete, and say what went wrong in their own words when a check fails. They also remember which older backups already opened, so those aren't decrypted again on every run. A file that changed size is opened again, and so is everything after you change the folder or passphrase, even when a run was already going.
- Have TalkBack say which option is picked in lists like the backup destination, the restore mode, the scheduled backup interval and the physical keyboard layout. Each row read as a radio button without saying whether it was checked.
- Build with Kotlin 2.4.21. Kotlin 2.4.10 had a security bug (CVE-2026-53914) where a tampered build cache could run code during a build, and release builds now refuse a compiler that still has it.
- Stop next-word learning from pairing words across text fields. Moving to another field resets what the keyboard remembers about the words you just typed, but that reset could land halfway through learning a word, so the first word in the new field was saved as following the last one in the old field, sometimes in another language.
- Leave a cached file alone at startup when it can't be moved out of the way. The cleanup deleted it in place instead, which could take a file an import was still copying.
- Stop two voice model installs or deletes from running over each other. The lock only covered one part of the app at a time, so a delete could remove a model folder another install was still copying into.
- Guard glide typing's list of alternative words. The suggestion row reads and trims it in the background while typing adds to it, and nothing kept the two from running at the same time.
- Keep shift and the keyboard mode from sticking on an old state. Two changes made at nearly the same moment on different threads could reach the keyboard out of order, and it then showed the older one until something else changed.
- Keep settings light with the "System default (AMOLED)" theme while the phone is in light mode. It turned the settings backgrounds black even in light mode, while text, icons and the status bar stayed set up for a light screen. It now looks the same as "System default" until the phone switches to dark, and then uses pure black.
- Make the settings home fit at 200% text size. On a narrow phone with large text, the status row, the section buttons and the quick actions kept their columns and cut labels like "Import data" short with "...". The grids now drop to fewer columns when they run out of room, and button labels can take two lines, so every label shows in full.
- Remove a clip from history when you clear it right after copying. Its history entry was added a moment after the clip showed up, so a clear in between missed it and left the entry and its picture behind. Two copies made back to back also land in history in the order you made them, and copying the same text twice in a row keeps one entry
- Fix the Obtainium link in the README. It still looked for `app-release` APKs, which releases stopped shipping, so adding SwiftFloris from that link found no update even after the Obtainium files themselves were fixed.
- Say a clipboard delete or clear is done only once it is. Deleting a clip or clearing history said so right away while the work ran in the background, so a picture whose file couldn't be deleted stayed on the phone behind a "deleted" message. The message now waits for the result. If anything stays behind, you get "Couldn't delete everything" with Retry, and what's left stays in clipboard history where you can see it. Closing the clipboard screen doesn't cut a delete short anymore, a clip you pin while a clear is running stays put, and the automatic cleanup of old clips keeps any clip whose file it couldn't delete and tries again next time.
- Keep a file you share to SwiftFloris when the app wasn't running. The cleanup of the last run's cache ran in the background while the import copied into the same folder, so it could delete the copy. Old cache is now moved out of the way first and deleted from there.
- Handle a shared file, an opened link or the language shortcut once. Turning the phone, or coming back after Android closed the app in the background, ran the request again, so the import screen copied the same files a second time and settings jumped back to the language page. Each request runs once now, and one that comes in while another is still loading takes its place. A copy that gets interrupted, say by turning the phone halfway through, now stops instead of finishing next to the new one, and doesn't leave its files in the cache. The import screen also remembers which files it was showing, so after Android closes the app it copies them again instead of coming back empty.
- Stop old suggestions from replacing newer ones. Suggestions are worked out in the background after each change, and a slow round for an earlier word could finish last and put its words back in the row, even after the row had been cleared. Only the newest round shows its suggestions now, and older ones are cancelled.
- Keep clipboard history in the order you copied things. Copying a picture or video takes a moment to save, and if you copied something else or cleared the clipboard before it finished, the older clip still showed up afterwards as the newest one and replaced what you'd copied. A clip that gets overtaken like that is now dropped, along with its saved file. And if a picture or video couldn't be read when you copied it, copying it again brings it in now. Before, the second copy was skipped as a repeat of the first. The same went for copying something again after clearing it or picking another clip in the keyboard, which now brings it back.
- Make "Erase everything" delete your personal dictionary even when it's turned off. It only cleared the dictionary while it was open, so with the dictionary turned off it said everything was erased, and your words came back the moment you turned it on again. Now it deletes the dictionary's files directly, including any copy kept after an earlier reset. If a file can't be deleted, it tells you which one instead of saying it's done.
- Keep your imported words when you close the import summary with Back or a tap outside it. Either one ran Undo and took the whole import back out. Only the Undo button does that now.
- Make dictionary imports all or nothing. Each word was saved on its own, so an import that failed halfway left the first half in your dictionary, with words you already had overwritten and no Undo for any of it. The whole file now goes in as one change, older backup files included, and a failure leaves the dictionary exactly as it was. Undo works the same way. It used to crash the settings screen because it ran on the main thread. Now it runs in the background, and if it can't remove the words the import summary stays open so you can try again.
- Keep your personal dictionary when it can't be opened for a moment. Any failed read while opening it counted as corruption, so a busy database or a brief storage hiccup moved your words aside and started you on an empty dictionary. Now only a file that's actually damaged gets replaced. The damaged copy stays on the device, and the dictionary screen tells you it happened until you dismiss the message, even after a restart. Anything else leaves your words where they are, and the keyboard tries again a little later (sooner if you tap Retry on the dictionary screen). Clipboard history also treats a file the encryption library can't read at all as damaged now, instead of retrying it forever.
- Show autofill chips on the first login field you open after the keyboard starts. Android asks the keyboard for its chip request before the keyboard has drawn anything, and the chip colors were only read once it had, so that first request was dropped and the password manager showed its own popup instead. The colors now come straight from the active theme, so the request goes out every time. The chip size had the same timing problem. The guessed height was smaller than the real one, so the chip that came back couldn't be drawn and was thrown away. The guessed width was too narrow, which cut "Bitwarden My vault" down to "Bitwar..." on every field of that form. The request now leaves room for the real height and works out the width from your saved keyboard size before anything is drawn, and a chip is always drawn within the size it was asked for. Part of issue #23.
- Show previews for images in clipboard history again. Since 1.9.58 every copied image showed "Something went wrong" instead of the picture. The size check that runs before decoding mistook its normal empty result for an image it couldn't open. Videos weren't affected. Part of issue #25.
- Let the debug build install next to the release. Both declared the same two signature permissions, so Android refused whichever one went on second. The debug build now leaves those declarations to the release.
- Stop the app behind the keyboard from redrawing its whole layout while you resize or move the keyboard. The keyboard reported its new top edge on every frame of the drag, so the app re-laid itself out under your finger. It now keeps its layout until you let go and adjusts once. Part of issue #23.
- Stop restyling the whole keyboard when only the shift state or mode changes. Each change rebuilt the Material You color palettes from scratch, and because a rebuilt palette never compares equal to the old one, every key looked up its style again. The palettes are now built once per theme and accent color.
- Resize the keyboard's labels when you let go of the resize handle, not on every frame of the drag. Text size follows the keyboard height, and following it live made every key look up its style again on each frame. Part of issue #23.
- Show a password manager's autofill chips even before you've answered the "App tint?" tip. The one-time tip took the suggestion strip first, so the chips stayed hidden until you tapped Skip or Enable. Autofill now comes first and the tip waits for an ordinary text field. Part of issue #23.
- Stop the emoji search from flashing "No emojis match this search." when you come back from typing. The emoji names load in the background each time the panel opens, and the search ran before they arrived. The panel now stays blank for that moment and only says nothing matched once the names are in. Part of issue #24.
- Stop resizing or moving the keyboard from making it bigger each time. With the Keyboard height slider set to anything other than 100%, the keyboard saved the height it was showing, the slider applied itself again the next time the keyboard came up, and every resize or move grew it (or shrank it) a little more. Part of issue #23.
- Make the emoji search, sticker search, clipboard search, emoji tag and pin group fields typeable. Android never connects a keyboard to its own text fields, so tapping them did nothing. Tapping one now sends the letter keys to it, with a bar above the keys showing what you've typed. Enter or the check mark applies it, and the back arrow cancels. The tag sheet also lists the names an emoji already has, so you can see what search finds it by. Part of issue #24.
- Make `:pensive` suggest 😔 whatever language the keyboard is in. Emoji names only ship for German, English, Spanish, French, Italian and Portuguese, and only the keyboard's own languages were searched, so everyone else got nothing. English names are now the fallback for shortcodes and for the emoji panel's search. A `:` shortcode also puts the emoji first in the suggestion row, where before it sat behind word guesses the three-slot row never showed. Reported as issue #24.
- Keep autofill chips inside the suggestion strip. Every chip was drawn as wide as the keyboard, so even a short one pushed its text past the edge of the screen. Chips now take the width their text needs, up to the room the strip has next to its buttons. Part of issue #23.
- Stop the keyboard crashing when you delete an image or video from clipboard history. The confirmation opened as a system dialog, and Android doesn't let a keyboard open one, so the whole keyboard went down instead. The confirmation, and the one for clearing history, now show inside the keyboard. Reported as issue #25.
- Stop the keyboard throwing away settings you chose. Nine preferences were pinned by the preference-migration step, which runs on every launch and again on every restore, so turning the number row off, switching either hint mode on, picking Follow-system theming, choosing a scrollable candidate row, the dynamic language/emoji utility key, the current-language space bar, or the FlorisBoard day and night themes only lasted until the keyboard next started. Restoring a backup could not bring any of them back either, because the same step runs on import. The starting point the fork wants is carried by each preference's own default, which is what a fresh install already got, so none of that was buying anything. Reported as issue #22.
- Size autofill chips to the keyboard rather than the screen. The password-manager and autofill suggestions above the keys were requested and inflated at the full display width, so on a floating, one-handed, split, or resized keyboard they ran off the edge. They now follow the width the keyboard is actually drawn at. Part of issue #23.
- Point the F-Droid recipe at an APK that exists. It named `app-release.apk` while every published release carries `SwiftFloris-v<version>-release.apk`, so the URL F-Droid downloads for its reproducible-build comparison had been returning 404 for as long as it had existed, and the comparison never ran without anyone noticing. The release signing certificate is now pinned in the recipe and published in the README, so an APK signed by anyone else fails the comparison instead of passing it.
- Make the long-press delay preference actually apply to the suggestion strip. The threshold for holding a suggestion to forget it was read once and never observed, so changing it in Settings had no effect for the life of the keyboard's composition.
- Commit the emoji you tapped after changing skin tone. An emoji key captured its emoji when it first appeared on screen, so if the preferred skin tone changed while the palette was open the key drew the new tone and inserted the old one.
- Build with AGP 9.3.2. Lint crashes on JDK 17 under 9.3.1, and JDK 17 is what the F-Droid recipe installs, so the build F-Droid runs could fail where the local one passes.
- Make Obtainium actually see new releases. The APK filter in both Obtainium manifests looked for `app-release.apk`, and no release this project has ever published was named that, so the update channel the README recommends matched nothing and silently offered no updates. It now matches the `SwiftFloris-v<version>-release.apk` assets that really ship, and the release gate resolves the newest release to confirm at least one asset matches.
- Order long-press popups by the number hint mode again. When the symbol hint was set to hint priority, the code re-checked the symbol hint mode instead of the number hint mode, so one branch always won and the number hint setting was ignored: the main character and the number hint came out in the wrong order for every mode except hint priority.

## v1.9.66 (2026-08-29)

- New app icon: adaptive, themed (monochrome) and legacy variants regenerated from the 2026-08 icon set.
- Keep the release-front-door fixture current with the developer-verification freshness check so the full local release gate can run on Windows.
- Pin the remaining Robolectric tests to API 35 so the target SDK 37 shadow gate can exercise them with the newest SDK Robolectric currently supports.

## v1.9.65 (2026-08-25)

- Respect "Remove animations" as soon as it is switched on. The animator duration scale lives in `Settings.Global` and changing it produces no configuration change, so a value keyed on the configuration kept reporting whatever was true when the screen was composed. It is observed now, like the other global settings this app reads.
- Show an updated layout extension without restarting. The layout and popup caches key on a component name rather than on the archive behind it, and nothing cleared them, so an extension that was updated or reinstalled kept serving whatever had been parsed from the previous `.flex`. The keyboard cache was already cleared on an extension change and went straight back to the stale entry. A read that failed is also no longer cached forever: a truncated archive or a transient IO error used to make that layout permanently unloadable for the rest of the process.
- Make a custom keypress sound take effect when you pick it. The controller loaded each sample into a SoundPool once, at construction, and lives as long as the keyboard service, so an imported sound stayed inaudible and a deleted one carried on playing from the handle it still held. Settings and the keyboard are both alive while the preview is on screen, which is exactly when people change these. The store now publishes a revision on every change and the controller reloads when it moves.
- Stop a personal-dictionary re-import stacking up duplicates. The locale column holds a raw string while the lookup binds a `FlorisLocale` that Room converts through `localeTag()`, which joins with an underscore. A SwiftKey export supplies BCP-47, so "en-GB" was written and "en_GB" was searched for: the existing row was never found, and importing the same file twice inserted every word again instead of updating it. Both import paths now store the spelling the lookup uses.
- Reject a dictionary pack whose descriptor names the wrong format, and stop one bad asset ending suggestions. `fldicAssetPath` was validated for blank, absolute and `..` but never for the extension its own name promises, and the loader picks its decoder off that extension: anything else went to the JSON parser, which throws on a binary dictionary. The descriptor now requires the format at enrolment, and a decode that fails anyway skips that asset and keeps whatever else loaded, matching what the supplemental-English loader beside it already did.
- Stop a snippet import writing a file nobody can see or delete. The Settings screen passes `uri.lastPathSegment`, which for a SAF document URI is an id such as `msf:1000000123` rather than a filename, and mapping the disallowed characters to underscores took the extension with them. The loader lists `.yml` and `.yaml` only, so the import reported the triggers it had found, wrote the file, and then the file was absent from the list and unreachable from the delete action. Imported names now keep a loadable extension.
- Stop an unreadable personal-dictionary file from taking the keyboard down. `learn` is fire-and-forget, so nothing up the stack could catch what its coroutine threw, and an unhandled exception in a launched coroutine reaches the thread's default handler whatever `SupervisorJob` does about siblings. A torn TSV therefore crashed the process on the first word it tried to learn, then again on the next one, because a failed load cached nothing to short-circuit the retry. The three n-gram stores now degrade and log; the suspending variants still tell their caller. One unreadable locale also used to empty the whole Learned entries screen and the typing stats count, which now skip it and show the rest.
- Answer Android 16 when it asks where the keyboard's language settings are. The platform reads `android:languageSettingsActivity` off the `<input-method>` element and starts that component directly, and with nothing declaring it the system's link had nowhere to go. It now opens the subtype list, which is where a language actually gets added, and a rotation no longer pushes a second copy onto the back stack. The language-pack screen also offers Add keyboard language unconditionally: two packs ship in assets, so an affordance that only appeared in the empty state was one no real user ever saw. Together with the reworded copy, that closes the path Discussion #21 got lost on.
- Check the app's own native libraries for 16 KB page compatibility. Only addon APKs were verified before, while the app ships SQLCipher across four ABIs; a library linked for 4 KB pages does not load on an Android 15 or newer 16 KB device, it crashes at first use. The new gate reads every `lib/**/*.so` out of the built APK and requires it to be stored, to start on a 16384-byte boundary, and to declare `p_align` of at least 16384 on every `PT_LOAD` segment. All eight libraries pass.
- Emit a genuinely unsigned release APK when no release keystore is configured, so the artifact F-Droid builds from matches the filename its recipe declares and can start a reproducible comparison. Also removes an `AntiFeatures: KnownVuln` entry whose description read "None known", which F-Droid applies on the presence of the key rather than the prose under it.
- Apply Advanced Protection changes without restarting the keyboard. Clipboard retention and addon enrolment already re-read the live state at every decision, but private typing was decided when a field was focused, so a session already in progress kept learning until the user moved on, and the privacy posture screen kept showing whatever it was composed with. One platform callback per process now carries the change to both. The live read stays authoritative, so a callback that never arrives cannot leave a stale answer enforcing anything.
- Respect an editor that forbids generative text replacement. Android 16 lets an editor set `isWritingToolsEnabled` to false, which is a stronger statement than the keyboard's own consent preference. The rewrite router now suppresses ahead of the cache, so a result cached for a field that allowed rewriting cannot be served into one that forbids it. Editors below API 36 cannot express the objection and are unchanged.
- Bound the error text that reaches toasts, notice cards and dialogs. All 24 paths that show a caught throwable to a person now route through one helper that collapses line breaks and control characters to spaces, drops the bidi overrides that reverse how a line reads, and cuts the result at 200 characters without splitting a surrogate pair. `ZipUtils` quotes a rejected archive entry name straight into its `SecurityException`, so a crafted archive previously chose up to 255 characters of what the user saw. Joiners that carry meaning in Arabic, Persian and Indic scripts and in emoji sequences are kept. The full cause goes to the log at every one of those sites, several of which logged nothing before.
- Stop curly placeholder substitution from rescanning its own output. A substituted value that carried the same placeholder was substituted into itself until the heap ran out, and ZipUtils echoes a rejected archive entry name into the message RestoreScreen interpolates, so a crafted backup archive took Settings down through the guard that caught it. Both overloads now scan the template once and never re-examine what they inserted.
- Stop settings captions announcing as unusable buttons. `FlorisOutlinedBox` attached a clickable modifier unconditionally, so the 92 boxes with a decorative title reached TalkBack as disabled buttons, while the one that navigates carried no button role and only a 23 dp target. List rows that navigate or apply a preset gained a button role, the extension add-file icon gained a label, and the subtype panel header lost a `clickable(false)` no-op.
- Make emoji sheet validation errors readable on light themes. `media-emoji-pin-sheet-error` is defined in none of the 21 bundled stylesheets, so the hardcoded Material 3 dark-scheme tone was what every user saw, at roughly 2:1 on the light themes. The tone is now chosen against the resolved sheet background.
- Keep a single failure from disabling a subsystem for the session. `SubtypeManager` and the Latin pre-warm scope were the only long-lived scopes in `ime/` without a `SupervisorJob`, so one throw left language switching or index pre-warming silently dead until the process restarted.
- Stop the crash dialog losing the report it exists to send. Reading the stacktraces deletes them, and the activity handles only rotation, so a theme, font-scale or locale change rebuilt an empty report. The report now survives recreation.
- Stop glide trails growing for the whole keyboard session. The shared fade buffer was never pruned, so retained memory and the per-frame scan grew with every word typed, and each in-flight trace was rebuilt per motion event rather than appended to.
- Stop the setup screen relaunching itself twice a second. Its captured state stays frozen for one effect run, so the relaunch condition stayed true until the settings observer caught up; the poll now stops once it has acted and never starts when it cannot.
- Say what the language pack screen is for. Discussion #21 is a user hunting for Portuguese there while `pt.fldic` was already bundled, guided by copy that read like the place to add a language and a button labelled "Add subtype".
- Count things correctly. Six strings ran every quantity through one English form, so a single item read "1 triggers", "1 apps" and "Keep 1 verified archives".
- Give every neutral hairline one opacity. Dividers and container borders drew `outlineVariant` at five different alphas across thirteen call sites.
- Stop silently dropping Gboard dictionary entries that contain a slash. The entry matcher excluded `/` from the attribute run, so "24/7", "km/h" and "n/a" never matched and were discarded. The import only reports failure when zero entries parse, so a file with other valid words imported "successfully" while losing them.
- Stop tracking generated Python bytecode, and reject it in the hygiene gate.
- Fix nine awkward or ungrammatical user-facing strings, an incognito toast that claimed a manual toggle changed a default, nine em dashes against the project style, four spellings of "spacebar", and British spellings in the en-US default.
- Cut Kotlin compiler warnings from 30 to 22, and match `LayoutTypeSerializer`'s visibility to the type it serializes so it cannot fail to resolve at a use site.

## v1.9.63 (2026-08-22)

- Fail closed across Android backup transports. Android 8 exports nothing, encrypted cloud backup and Android 12 device transfer use the portable inventory, and Android 16 QPR2 cross-platform transfer exports nothing. Persisted-store discovery, selected-resource parsing, and transport-policy tests guard the boundary.
- Keep raw-content developer tools out of production APKs. Debug overlays now clear and hide clipboard, editor, spelling, and inline-autofill content in password, incognito, and no-learning sessions. Release APK and build-variant gates enforce both boundaries.
- Repair the backup and restore benchmark harness for the current archive workspace and persisted-content inventory APIs.
- Keep Android's tracked `src/release` code distinct from generated release output in the repository-hygiene gate. Its fixture preserves both decisions, and the root-crash-log self-test now finds Git Bash directly on Windows instead of falling into an unavailable WSL relay.
- Stop adaptive-touch refinement and persistence in password, incognito, and app-declared no-personalized-learning sessions. Normal text fields still learn, and focused privacy tests cover all four paths.
- Restore the canonical release trust registry to Build Tools 37.0.0 and SQLCipher 4.18.0. Exact drift fixtures now fail when either live owner changes without a matching registry update.

## v1.9.62 (2026-08-21)

- Evaluate offline rule-based proofreading without adding a production engine. LanguageTool's licence, Java surface, and per-language data cost rule it out for the base APK. Harper is the cleaner future addon candidate, but it has no official Android library and supports English only.
- Add a debug-only English agreement rule and device test. The test reaches `FlorisSpellCheckerService` through Android's `TextServicesManager` and proves that the existing sentence path returns `RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR` on a physical Android 16 device.

## v1.9.61: 2026-08-20

- Add one snackbar surface to Settings and the keyboard panels. Clipboard text deletion and per-app profile deletion can now be undone, media deletion asks for confirmation, and Delete no longer sits in the profile dialog's dismiss controls.
- Settings search now scrolls to the matching preference row and briefly highlights it. The shared preference wrappers keep the destination screens unchanged while the row reports its position, and the search card disappears after the row is reached.
- Replace security-path source checks with behavior tests for encrypted stores, dictionary learning, clipboard policy, backup, sync, MCP consent, privacy gates, and sticker manifest replacement. Roborazzi capture classes are explicitly named in the plain unit-test report, with fixed preview density and font scale for repeatable captures.
- Share the long-lived `NlpAddonHub` from `NlpManager` with the translation quick action, so future per-instance addon state cannot diverge between typing and smartbar calls.
- Raise material-kolor to 5.0.0 now that the Compose BOM is enforced. The full unit suite and Roborazzi theme captures remain green.
- Decode alternating two-pointer glides as separate traces, committing each word in finger-release order. The existing single-pointer detector behavior remains covered, and the active trail now renders both traces.
- Add a fork-provenance section to README with the install package identity, merged release permission surface, certificate capture commands, and reproducible-build review checklist. The official release fingerprint remains pending until a signed release APK is published.
- Add clipboard privacy controls: long-press can mark or unmark a text entry as sensitive, and a deliberate reveal shows it for four seconds only while the device is unlocked and the keyboard is not incognito. The default accessibility label stays masked.
- Add an opt-in scrambled keypad for numeric password fields. The permutation is generated per field focus, and the key label, TalkBack text, and committed digit stay aligned. Normal numeric and phone fields are unchanged.
- Add optional local keypress sounds for standard, delete, return, and spacebar keys. Files are imported through the system picker, loaded into a pooled player, and carried by the existing backup flow. System effects remain the default until a class is selected.
- Add a spacebar touchpad mode that tracks horizontal and vertical drags, preserves fractional movement at a configurable ratio, and clamps horizontal targets to the editor's safe bounds. Existing discrete spacebar actions remain available when the mode is off.
- Align split-keyboard gutters with vertical hinge bounds reported by AndroidX Window Manager. Each row scales its halves to the hinge, while non-folding windows keep the centered layout.
- Add a runtime warning when bundled emoji assets declare a different CLDR or Emoji version than the checked-in expectation.
- Split offensive suggestion filtering into the existing all-offensive switch and a slurs-only option, with exact-token filtering shared by word suggestions and spell-check corrections.
- Add a 50% to 100% autocorrect confidence slider. The local typing-quality scorecard records every threshold and selects the measured 50% default from the checked-in replay corpus.
- Stop shipping a Material 3 version no file in the repository names. Four
  Compose Multiplatform dependencies each constrain `androidx.compose.material3`
  to a pre-release alpha, and Gradle picked that over the Compose BOM's stable
  pin, so the reproducible-build doc's claim that every Compose version is pinned
  here was not true. The BOM is now applied as an `enforcedPlatform`, and a gate
  fails the build if any Compose artifact resolves to a pre-release again.
- Add "Report a problem" to Settings → About. Reaching the issue tracker from
  inside the app previously required an actual crash: the version, build type,
  commit hash, install source, device and Android version the issue templates ask
  for were assembled in the crash dialog and nowhere else, so anyone reporting a
  bug that did not crash had to find all of it by hand. The row copies the same
  block, with the redaction reminder, and opens the bug report form.
- Let the crash report and the debug log be shared to another app. Both were
  copy-to-clipboard only, which meant pasting by hand on the device that is
  misbehaving. The redaction reminder travels with the shared text.
- Fix a crash reporter that could crash. It read every field on
  `Build.VERSION_CODES` as an integer, which holds for the constants a stock
  platform declares but throws the moment a non-integer field exists: so the
  failure would land while reporting a failure. It now reads only static integer
  fields and falls back to naming the SDK level.
- Stop clipboard search from copying the whole history on every keystroke. Each
  typed character lowercased every stored clip into a fresh string before
  searching it, so with a history of near-limit clips a single keystroke
  allocated megabytes on the thread drawing the panel. Matching now compares in
  place. A replay covering a full history of 64 KiB clips measures the
  allocation, because a wall-clock budget loose enough not to flake also passes
  with the defect present.
- Make settings search find every preference. It covered 103 of the 276 rows the
  settings screens declare, so Input Feedback matched none of its 16 preferences,
  Addons none of 8, MCP none of 9 and Gestures 7 of 28: searching "vibration
  strength" or "utility key action" returned nothing. The 173 missing rows are
  indexed, and a new gate fails the build when a screen declares a preference
  that search cannot reach: the old integrity test iterated the index itself, so
  it could only check what was already there and never saw an omission.
- Say on the MCP screen that no daemon runs in this build. The screen offered
  discovery review, a bridge switch and per-daemon toggles while nothing could
  be bound or dispatched, so turning a daemon on looked like it started
  something. Trust decisions and toggles are still saved and apply once binding
  returns. One flag now decides this for both the keyboard service and Settings,
  so the two cannot drift apart.
- Let glide typing work with a second finger on the keyboard. A finger already
  resting on the keys claimed the gesture detector and never let go, because a
  motionless finger is never classified as a glide, so the other hand could not
  swipe at all. Sliding movement was also matched against the wrong pointer,
  which dropped every move once the gliding finger was not the first one down.
  A glide already under way now keeps the trace when another finger lands, and
  the key a glide starts on is resolved from the gliding pointer rather than
  assumed to be the first: which is what suppresses glides that begin on
  delete, shift or space.
- Put SwiftFloris under Android Settings → Apps → Language on Android 13 and
  newer. The app declared no `android:localeConfig`, so the system's per-app
  language picker never listed it and the 43 shipped translations could only be
  reached from inside the app.
- Offer every shipped translation in the in-app language picker. Its list was
  written by hand and had fallen four behind, so the Asturian, Estonian,
  Albanian and Urdu translations shipped in the APK with no way to select them.
  The list is now generated from the same resource directories the build reads.
- Keep the in-app language choice and Android's per-app language in step: the
  picker writes through to the system setting, and a language chosen in system
  Settings wins at startup rather than being overwritten by the stored one.
- Bring the build and library pins current: Gradle 9.7.1, AGP 9.3.1, Compose BOM
  2026.08.00, AndroidX Core 1.19.0, AndroidX SQLite 2.7.0, SQLCipher 4.18.0, Coil
  3.5.0, KSP 2.3.11, build tools 37.0.0 to match `compileSdk 37`, AboutLibraries
  15.1.0, Kotest 6.2.4, Roborazzi 1.72.0, Kover 0.9.9, JUnit Vintage 6.1.3 and
  ComposablePreviewScanner 0.9.3. Kotlin stays on 2.4.10 and Robolectric on
  4.16.1, both for reasons recorded in `docs/DEPENDENCY_TRIAGE.md`.
- Stop two tests from pinning dependency versions as literal source text. The
  SQLCipher, SQLite and Tink version literals had a second owner in a test that
  had to be edited on every bump, so they now live only in the freshness gate
  that already enforces them; the AboutLibraries assertion reads its expected
  version from the version catalog, so a pin bump that leaves the generated
  licence metadata stale still fails.

## v1.9.60: 2026-08-20

- Recognise Transcribro as an offline voice input provider, alongside FUTO Voice
  Input, WhisperInput and Whisper.
- Name every store a backup archive leaves behind, rendered from the backup
  inventory instead of a hand-written sentence that listed four of thirteen and
  never mentioned the personal dictionary. A new excluded store without a label
  now fails a test rather than going unlisted.
- Say plainly that importing over an existing word replaces its frequency and
  shortcut, and that undo does not bring the old values back.
- Say that the privacy audit log covers the current keyboard session, so an
  empty log after a restart no longer reads as "no AI call ever happened".
- Keep the personal dictionary on the language you are editing when you delete
  its last word. It previously bounced back to the language list with no
  explanation, which also made the per-language empty state unreachable; that
  state now renders and names the language.
- Give the backup exclude list one owner. The `verifyDataExtractionRules` build
  task pinned a hand-written 13 of the 22 paths in `data_extraction_rules.xml`,
  so removing the Tasker HMAC secret, the clipboard history and its keys, or the
  scheduled-backup preferences left it green; `BackupDataInventoryTest` already
  matched both rule sets exactly and now owns the check alone.
- Give every security-relevant dependency a freshness floor. The gate checked a
  single dependency and printed `OK (1 checked dependency floor(s))`, which read
  like a pass while Tink, Room, androidx-sqlite, Kotlin, KSP and AGP had no floor
  at all; it now fails when a security-relevant catalog pin has no reviewed
  entry. The override matcher was inverted and could let one coordinate's
  override suppress another's floor, so overrides now match on both fields and
  are rejected at load time when either is missing.
- Record the Tink CVE-2026-15432 triage: the reported timing side channel is in
  `ChunkedMacVerification`, which this app never calls, so the pin stays at
  1.23.0 with a floor that surfaces the patched release when it ships.
- Repair two dead ends in the migration flow: the "SwiftFloris encrypted backup
  (.sfexp)" tile now opens the personal dictionary import picker, which detects
  the encrypted envelope and prompts for the passphrase, instead of the archive
  Restore screen that cannot read it; and an archive storing its dictionary as a
  SQLite snapshot now explains how to re-export it rather than pointing at an
  import path that does not exist.
- Correct four public docs that described protections the code does not have:
  the personal dictionary passphrase is Keystore-wrapped rather than
  Keystore-held, the Tasker receiver is gated by a default-off preference and a
  per-install HMAC signature rather than a signature permission (its `adb`
  examples were rejected at runtime and are replaced with the real setup flow),
  the reduced-motion guard reads `ANIMATOR_DURATION_SCALE` through
  `rememberReducedMotion()` rather than a Compose API that does not exist, and
  the release checklist now names the merged-manifest gate that actually
  enforces the permission guarantee.
- Raise the shared Settings widgets to the 48 dp WCAG 2.5.5 touch-target floor
  through a single named constant, including the custom layout editor's key
  buttons, and cover the floor with a test that measures the rendered target
  rather than matching source text.
- Fix a context-chain walk that could not terminate: configuring the IME system
  bars from a wrapped context (a Compose dialog or themed wrapper) spun on the
  main thread instead of resolving, and a missing window now skips the system
  bar setup rather than crashing.
- Derive theme contrast coverage from every foreground-bearing selector in each
  bundled stylesheet instead of a ten-selector list, report all violations at
  once, and name each WCAG exemption (inactive components, decorative
  separators, and the 3:1 non-text floor for glyph elements) in the gate.
- Raise contrast in the bundled themes the widened gate exposed: the extracted
  landscape action and the borderless focused key popup no longer draw light
  text on a light surface, and the glide trail, focused emoji tab, and floating
  resize handle use accent-variant tints that clear the non-text floor in the
  Floris Day, SwiftKey Pure, and M3E Nord themes.
- Warn inline in the theme editor when an edited foreground/background pair
  falls below the 4.5:1 WCAG AA text floor.
- Make the OSV release gate classify numeric CVSS scores and supported vectors
  correctly, fail closed on unknown severity, and run a regression self-test
  beside the release gate.
- Keep smart-compose ghost text off during incognito sessions, including in
  ordinary non-sensitive editors, and retain request-scoped privacy gating.
- Make release evidence exhaustive: discover every gate and Python self-test,
  record the two connected-device gates as explicit operator-run checks, and
  repair the repo-hygiene allowlist drift.
- Keep Android system dictionary access read-only in SwiftFloris: system entries
  remain browsable and exportable, while add, edit, delete, and import actions
  stay in the internal dictionary and Android system dictionary settings.
- Unify addon/MCP enrollment, merged-manifest, and addon-APK permission gates
  around the same fail-closed allowlist, including a SEND_SMS fixture regression
  check and the shared trust-capabilities registry.
- Expand live-document integrity to scan planning and untracked Markdown, reject
  dead roadmap/workflow references, and validate the blocked-roadmap structure.
- Require the F-Droid recipe's `commit:` ref to resolve on both local and origin
  tag sets, with a fixture proving an unresolvable ref fails the release gate.
- Route live smart-compose and translation calls through the audited addon hub,
  require explicit MCP consent/disable gates, remove the dead direct dispatcher,
  and pause MCP daemon binding until a real audited keyboard action exists.
- Preserve unreadable sticker, correction-prior, and personal n-gram stores
  instead of treating them as empty, and only quarantine clipboard history on
  explicit SQLite corruption evidence while keeping transient read failures
  retryable.
- Make MCP lifecycle startup single-shot even when consent is disabled and
  serialize start, rescan, retry, and teardown so a Settings rescan cannot
  repopulate registries during IME shutdown.
- Remove the maintainer device serial from benchmark baselines and emit a
  stable SHA-256 device key derived from manufacturer, model, and SDK, with
  repository hygiene rejecting serial fields in tracked documentation.
- Reconcile stale device-tier blockers with the attached API-36 emulator:
  preserve the real app/password-manager/API-37 gaps, close the already-shipped
  instrumented smoke coverage, and keep the missing MCP addon sample blocked.
- Declare API-33 stylus-handwriting and TalkBack touch-exploration inline-
  autofill capabilities in the IME manifest, with a contract test and updated
  accessibility/autofill verification guidance.
- Keep theme, extension, and addon settings loading states separate from their
  empty states, automatically start the first addon scan, and add a Roborazzi
  baseline for the theme loading surface.

## v1.9.59: 2026-08-11

- Screen addon and MCP daemon enrolment against a permission allowlist so
  transports that need no `INTERNET` permission: SMS, Bluetooth,
  nearby-devices, shared storage: are rejected alongside the network ones.
- Clear leftover cache off the main thread at startup, so a cold keyboard
  start no longer blocks on a recursive delete.
- Replace a typed word by selecting it and committing over the selection
  instead of marking a composing region, so rich-text and web editors that
  ignore composing regions no longer duplicate the word.
- Move the build to Android Gradle Plugin 9.3.0.
- React to Android Advanced Protection Mode (Android 16+): while it is on,
  learning from typed text, clipboard history persistence and new add-on
  enrolment are all held off, and the privacy posture screen says so.
- Reject unrenderable key code points during layout import instead of
  producing a blank, unlabelled key.
- Move the build to Kotlin 2.4.10.
- Offer line-start, line-end, text-start, text-end and Page Up/Down as
  quick actions, so cursor jumps no longer require knowing a swipe binding.
- Add an opt-in emoji suggestion mode that keeps the typed word and puts the
  emoji after it instead of replacing the word.
- Route all 42 shipped locales through the translation pipeline; eight,
  including Simplified Chinese and Urdu, previously had no mapping and could
  never round-trip. A gate now fails when a locale has no route.
- Declare the bundled emoji data as Unicode Emoji 17.0, which is what CLDR 48
  generated; the assets already carried every Emoji 17.0 character.

## v1.9.58: 2026-08-02

- Keep trust-capability evidence aligned with SQLCipher clipboard fallback and
  manifest permission-removal directives, and make the release-front-door
  locale gate portable across `python` and `python3` environments.
- Retain bounded, privacy-gated glide alternatives for unchanged committed words and restore them when the cursor returns to the word.
- Search emoji across the active and enrolled subtype locales with ordered fallback matching and bounded deduplication.
- Expand headless settings screenshot coverage across compact, wide-landscape, RTL, and 200% font-scale states, with shared loading/error/empty semantics and production color contrast checks.
- Refresh Tink to 1.23.0, Roborazzi to 1.70.0, and Kotest to 6.2.3 with the existing verification gates retained.
- Localize privacy-audit record labels, plural summaries, and timestamps while keeping the JSON export schema locale-independent.
- Centralize editor input-class/variation/flag compatibility, clear stale candidates for host-owned completion fields, and add headless restart and hardware-key contract coverage.
- Add a deterministic trust-critical locale coverage gate with translated-resource ratchets, explicit reviewed UI locale policy, typing-language separation, `en-XA`/`ar-XB` pseudolocale contracts, and hard-coded critical-copy detection.
- Centralize bounded keyboard-mode/context transitions so clipboard and media panels restore the prior symbols/numeric mode and clear stale history across editor, privacy, and window boundaries.
- Wire custom emoji tags through long-press palette actions, palette search, emoji suggestions, persisted settings management, locale-root normalization, and atomic file replacement.
- Preserve existing emoji pin-group files when an atomic replacement fails, including on Windows hosts that reject rename-over-existing.
- Surface snippet load and delete failures, move snippet file work to Dispatchers.IO, and expose per-file trigger counts from state.
- Route emoji and sticker empty-state copy through the keyboard theme text pipeline, including expanded-font-scale Roborazzi coverage.
- Resolve custom-layout labels and generated extension metadata through localized resources while preserving stable on-disk slugs.
- Add programmable Page Up/Page Down key codes, Android page-key dispatch, custom-layout editor support, localized labels, and Terminal/Navigation preset coverage.
- Import Unicode Keyboard3 XML as hardened local keyboard extensions with bounded XML parsing, versioned bundled-CLDR allowlists, deterministic compilation, source provenance, and security/conformance diagnostics.

## Roadmap archive: 2026-08-10: ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
`Roadmap_Blocked.md`.

---

## Research-Driven Additions

### P3
## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P0

### P1

### P2

### P3

## Research-Driven Additions

### P2

### P3

## Audit Findings: 2026-08-02

Baseline for this pass: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`
→ **BUILD SUCCESSFUL in 4m 34s**, no failing tests, no lint failures. Nothing
below is a pre-existing baseline failure; every item is a defect found by
reading and tracing the code against that green baseline.

### P2

### P3
```

</details>
