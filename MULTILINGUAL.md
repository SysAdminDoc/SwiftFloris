# SwiftFloris Multilingual Support Test Guide

This document outlines how to test spell checking and suggestions across all supported languages.

## Supported Languages

SwiftFloris includes dictionaries and language models for:
- 🇬🇧 **English** (en) - 58,860 words
- 🇩🇪 **German** (de) - 398,555 words
- 🇪🇸 **Spanish** (es) - 412,314 words
- 🇫🇷 **French** (fr) - 476,697 words
- 🇮🇹 **Italian** (it) - 414,437 words
- 🇵🇹 **Portuguese** (pt) - 512,471 words

## How Multilingual Support Works

1. **Lazy Loading**: Dictionaries are loaded on-demand when you switch to that language
2. **Automatic**: No manual configuration needed - just change your system language or keyboard subtype
3. **Persistent Caching**: Loaded dictionaries remain in memory for quick access
4. **Edit Distance**: Corrections use Levenshtein distance algorithm (distance ≤2)

## Testing Spell Checking

### English (en_US)
```
Type: "helo"
Expected suggestion: "hello"

Type: "wrld"  
Expected suggestion: "world"

Type: "teh"
Expected suggestion: "the"
```

### German (de_DE)
```
Type: "guten tg"
Expected suggestion: "guten tag" (good day)

Type: "halo"
Expected suggestion: "hallo" (hello)

Type: "schl"
Expected suggestions: "schlacht" (battle), "schlag" (hit), etc.
```

### French (fr_FR)
```
Type: "bonjor"
Expected suggestion: "bonjour" (hello)

Type: "au rvoir"
Expected suggestion: "au revoir" (goodbye)

Type: "merci bocop"
Expected suggestion: "merci beaucoup" (thank you very much)
```

### Spanish (es_ES)
```
Type: "hola muno"
Expected suggestion: "mundo" (world)

Type: "como stás"
Expected suggestion: "cómo estás" (how are you)

Type: "gracias"
Expected: No suggestion (correctly spelled)
```

### Italian (it_IT)
```
Type: "ciao mondo"
Expected: No suggestions (correctly spelled)

Type: "buongiorno"
Expected: No suggestions (correctly spelled)

Type: "grasie"
Expected suggestion: "grazie" (thank you)
```

### Portuguese (pt_PT/pt_BR)
```
Type: "ola mundo"
Expected suggestion: "olá mundo" (hello world)

Type: "obrigado"
Expected: No suggestion (correctly spelled)

Type: "tudo bem"
Expected: No suggestions (correctly spelled)
```

## Testing Word Predictions

Word predictions are available for all languages using n-gram models. Currently, detailed n-gram data is loaded for English, but the framework supports all languages.

```
Type: "the"
Expected suggestions: "the", "them", "their", "these", "then"

Type: "in the"
Expected next-word suggestions based on context
```

## Changing Language/Subtype

1. **Via Settings**:
   - Open SwiftFloris Settings
   - Navigate to Languages or Input Methods
   - Select desired language

2. **Via System**:
   - Settings → Languages & Input
   - Select SwiftFloris keyboard
   - Choose language/subtype

3. **Via Keyboard**:
   - Long-press Space or use swipe action
   - Select from available subtypes

## Performance Notes

- **First load**: ~500ms per language (dictionary parsing)
- **Subsequent loads**: <10ms (cached)
- **Spell check latency**: <100ms per word
- **Memory usage**: ~5-10MB per loaded language

## Known Limitations

1. **No context-aware corrections** (e.g., "their" vs "there")
2. **No stemming** (e.g., "run" ≠ "running")
3. **Limited n-gram data** (only English has detailed models)
4. **No abbreviation handling** (e.g., "mr." → "mister")

## Troubleshooting

**Suggestions not appearing?**
- Ensure auto-correction is enabled in settings
- Check that dictionary loaded (look for log messages)
- Try typing a clearly misspelled word (distance ≤2)

**Wrong language loading?**
- Verify system keyboard language matches expected language
- Clear app cache: Settings → Apps → SwiftFloris → Storage → Clear Cache
- Restart IME service: disable/re-enable SwiftFloris

**Performance issues?**
- Check device RAM (needs ~20MB per language)
- Reduce max suggestion count in settings
- Use only most-used languages to minimize memory

## Future Improvements

- [ ] Stemming support (run/running/runs)
- [ ] Context-aware suggestions
- [ ] User-trained models (learner mode)
- [ ] Compound word support (German: "Kindergarten")
- [ ] Phonetic suggestion fallback
- [ ] More complete n-gram models for all languages

## Contributing

To improve multilingual support:
1. Submit better dictionaries via GitHub Issues
2. Request new languages
3. Propose algorithm improvements
4. Test on various devices and report results

---

**Last Updated**: 2026-05-04 (v1.2.0+)
**Maintained By**: SwiftFloris Contributors
