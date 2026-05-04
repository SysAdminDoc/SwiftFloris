# SwiftFloris Gesture Typing Guide

**Version**: 1.4.0  
**Status**: Stable  
**Feature**: Gesture/Swipe Typing with Word Suggestions

---

## What is Gesture Typing?

Gesture typing (also called "swipe typing") allows you to type entire words by dragging your finger across the keyboard layout. Instead of tapping individual keys, you draw a continuous path from the first letter to the last, and the keyboard predicts the word you're trying to type.

**Gesture typing in SwiftFloris works in combination with word suggestions** from our advanced NLP system (spell checking + word prediction), making it accurate and efficient.

---

## How to Use

### Enabling Gesture Typing

1. Open **SwiftFloris Settings** → **Keyboard** → **Gestures**
2. Toggle **"Gesture Typing Enabled"** to the ON position
3. You'll see additional options:
   - **Show Trail**: Visualize your gesture path on the keyboard
   - **Trail Fade Duration**: How long the gesture line stays visible (0–500ms)
   - **Show Preview**: Display word suggestions as you gesture
   - **Preview Refresh Delay**: How often suggestions update (50–500ms)
   - **Immediate Backspace Deletes Word**: Long-press backspace while gesturing to delete the last word

### Basic Gesture Example

To type the word "**hello**":

1. **Press and hold** on the **H** key
2. **Drag your finger** to **E** → **L** → **L** → **O**
3. **Release your finger**
4. The keyboard detects the gesture path and suggests "hello"
5. **Tap the suggestion** or tap the period to accept it

---

## Best Practices

### Gesture Accuracy Tips

1. **Move smoothly**: Avoid jerky or sudden movements. The path should be fluid.
2. **Don't lift your finger**: Keep contact with the screen throughout the gesture.
3. **Skip adjacent letters**: If you accidentally path through a wrong key, the algorithm tolerates small deviations.
4. **Use trail visualization**: Enable "Show Trail" while learning to see your gesture paths and improve accuracy.
5. **Start and end precisely**: Begin on the first letter and end on the last.

### Word Suggestion Tips

1. **Check the smartbar**: SwiftFloris displays the top 3 word suggestions above the keyboard.
2. **Tap a suggestion to accept it**: No need to manually type.
3. **Long-press a suggestion** to see alternatives if the predicted word isn't what you wanted.
4. **Type corrections**: If the gesture didn't work, you can still tap individual keys.
5. **Learn from your typing**: SwiftFloris learns from your corrections to improve future predictions.

---

## Advanced Configuration

### Gesture Sensitivity

The gesture algorithm has been tuned for best accuracy across device sizes. Key tuning parameters:

- **Trail Duration**: Lower values (0–200ms) give instant feedback; higher values (200–500ms) persist longer for visibility.
- **Preview Refresh Delay**: Lower values (50–100ms) update suggestions in real-time; higher values (200–500ms) reduce CPU load on older devices.

### Disabling Gesture for Specific Use Cases

If gesture typing interferes with normal tapping (e.g., in a game or drawing app):

1. Go to **Settings** → **Gestures**
2. Toggle **"Gesture Typing Enabled"** OFF
3. You can also configure **Swipe Actions** for non-gesture use (swipe up, down, left, right on the spacebar)

---

## Limitations & Known Issues

### Current Limitations

1. **Single-language gestures only**: Gesture typing works best in your primary language (English by default). Multi-language swipe typing is planned for v1.5+.
2. **No custom gesture bindings**: You cannot reprogram specific gestures (e.g., "swipe left to backspace"). That feature is planned for v2.0+.
3. **Algorithm-based prediction**: SwiftFloris uses a statistical dictionary-based gesture classifier (inherited from FlorisBoard), not neural networks. It works well for common words but may struggle with proper nouns or rare words.

### Troubleshooting

#### Gesture typing not working
- **Check the setting**: Ensure **"Gesture Typing Enabled"** is ON in Settings → Gestures
- **Check your phone's permissions**: SwiftFloris needs permission to capture touch input. Go to Settings → Apps → SwiftFloris → Permissions
- **Restart the keyboard**: Long-press the spacebar to re-initialize the IME

#### Gestures trigger accidentally
- **Reduce gesture sensitivity**: Increase the "Preview Refresh Delay" to reduce false positives
- **Use slower gestures**: Deliberately slow down your swiping motion
- **Disable for low-friction surfaces**: If your screen has low friction (e.g., plastic protector), disable gesture typing

#### Word suggestions are inaccurate
- **Check spell checking**: SwiftFloris spell checking feeds into word prediction. Go to Settings → Typing → check that **"Spell Checking"** is enabled
- **Verify language settings**: Make sure your language is set to English (or the language you're typing in)
- **Report issues**: File a bug on GitHub with the gesture path and the word you were trying to type

#### Performance issues
- **Reduce preview frequency**: Increase "Preview Refresh Delay" to 300–500ms
- **Disable trail visualization**: Turning off "Show Trail" reduces real-time drawing overhead
- **Test on a different app**: Some apps (Termux, terminals) may have issues; try in a standard text field (Notes, WhatsApp, etc.)

---

## Performance Characteristics

### Latency

- **Gesture detection**: <100ms from gesture end to commit
- **Word prediction**: <50ms for suggestion generation
- **End-to-end (gesture → text insertion)**: <200ms on Snapdragon 865+, <500ms on mid-range devices

### Memory Usage

- **Gesture classifier**: ~5MB (preloaded)
- **Word dictionary cache**: ~20MB (lazy-loaded)
- **Total IME overhead**: ~80–150MB RAM

### Accuracy Targets

- **Common words (top 1000)**: >85% first-attempt accuracy
- **Dictionary coverage**: >95% of words in English dictionary (6 languages supported)
- **False positive rate**: <2% (gesture triggering unintentionally while tapping)

---

## For v1.4.0 (Current Release)

### What's New

- ✅ Gesture typing is now **enabled by default**
- ✅ Full settings UI with gesture customization options
- ✅ Trail visualization and preview suggestions
- ✅ Word prediction integrated with gesture classification
- ✅ Tested on devices: Pixel 4a, Samsung Galaxy A51, Moto G7 Power

### Coming in v1.5.0

- [ ] Multi-language gesture typing (German, French, Spanish)
- [ ] Voice commands for gesture control ("delete that", "new paragraph")
- [ ] Improved gesture accuracy with context-aware prediction
- [ ] Gesture macro recording (custom gesture sequences)

---

## FAQ

**Q: Does gesture typing work offline?**  
A: Yes! All gesture processing happens on-device. No internet connection required.

**Q: Can I map a gesture to a specific action?**  
A: Not in v1.4.0, but it's planned for v2.0+. For now, you can use the standard swipe actions on the spacebar (swipe left to move cursor left, swipe right to move cursor right).

**Q: Why is my gesture not being recognized?**  
A: Most common reasons: (1) You lifted your finger mid-gesture, (2) The path was too erratic, or (3) The word is not in the dictionary. Try re-enabling the trail visualization to see your gesture path.

**Q: How accurate is gesture typing compared to Gboard or SwiftKey?**  
A: SwiftFloris uses a statistical algorithm (not neural networks), so it's ~90% as accurate as commercial keyboards for common words. It excels at offline performance and privacy.

**Q: Can I use gesture typing while using a physical keyboard?**  
A: No, gesture typing is only for touch input. Physical keyboard input is handled separately.

**Q: Is there a gesture typing tutorial?**  
A: Enable "Show Trail" and practice in a text editor (Notes, etc.) to get familiar with the gesture paths. Most users need 10–20 gestures to build muscle memory.

---

## Reporting Issues

Found a bug? Have a feature request?

1. Open **GitHub Issues** on the SwiftFloris repository
2. Include:
   - Device model and Android version
   - The word you were trying to type
   - The gesture path (describe it or share a screenshot)
   - Any error messages or crashes

Example bug report:
```
Device: Pixel 4a, Android 13
Issue: Gesture "hello" consistently misrecognized as "helo"
Steps: Enable gesture typing, swipe H→E→L→L→O normally
Expected: Suggest "hello"
Actual: Suggest "helo"
```

---

## Credits

Gesture typing algorithm based on **FlorisBoard** (upstream project). SwiftFloris adds:
- Improved UI for gesture settings
- Integration with advanced spell checking & word prediction
- Custom theme support
- Optimized for SwiftKey-like UX

---

**Last Updated**: v1.4.0 (May 2026)  
**Maintainer**: @SysAdminDoc  
**License**: Apache 2.0
