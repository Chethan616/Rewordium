# Google Play listing — Rewordium – Smart Keyboard

Package: `com.chethan616.keyboard` · Pricing: paid upfront (one-time purchase) · No in-app purchases, no ads, no subscriptions.

> The earlier app, Rewordium (`com.noxquill.rewordium`), has its own listing and documents on the `main` branch.

## App name (≤ 30)

```
Rewordium – Smart Keyboard
```

## Short description (≤ 80)

```
Smart keyboard: autocorrect, swipe typing & optional AI. Typing stays on device.
```

## Full description (≤ 4000)

```
Rewordium is a smart Android keyboard. Type faster with intelligent autocorrect, accurate swipe typing and suggestions that learn the way you write, while your typing stays on your phone.

KEYBOARD FIRST
• Intelligent autocorrect that leaves names and slang alone
• Accurate swipe (glide) typing
• Smart word suggestions and next-word predictions
• A personal dictionary that learns your words on your phone, with Incognito mode when you don't want it to learn
• Clipboard history, emoji search, emoticons, GIFs and stickers
• Make your own stickers or import sticker collections
• Number row, one-handed mode, adjustable height and key spacing

MAKE IT YOURS
• Ready-made theme packs, from playful to clean and professional
• A theme builder for colours, key shapes, fonts and depth
• A community gallery of themes made by other people
• Bouncy key animations and a mascot on your space bar

OPTIONAL AI WRITING TOOLS
Rewrite, fix grammar, change the tone, shorten, expand, summarize or translate right from the keyboard, or ask Jade, the in-app writing assistant.
• AI is optional. Text is sent to the AI provider only when you use an AI tool.
• The app includes a limited number of AI uses, shown in the app. They don't refill in this version. AI is not unlimited.
• When your included AI uses run out, the keyboard and every other feature keep working.
• Prefer your own AI provider? Add your own API key (Groq, OpenAI, Google Gemini or Anthropic).

PRIVACY-CONSCIOUS BY DESIGN
• Typing, autocorrect, predictions and the words the keyboard learns are processed on your phone
• No account needed
• No ads, and the app doesn't collect the advertising ID
• Suggestions and autocorrect are off in password fields
• The built-in AI runs on models hosted by Groq. Only the text you choose is sent, and only when you use an AI tool.
Read exactly what leaves your phone in our Privacy Policy and Data Safety page.

OPTIONAL WRITING ASSISTANT IN OTHER APPS
Rewordium can also offer a floating writing assistant inside other apps. It uses Android's Accessibility Service API, is off until you turn it on, and the app explains what it does first. When you use it, it reads the text field you're writing in (and, for reply suggestions, the visible conversation) and sends that text to the AI provider only when you run a tool.

ONE PURCHASE
Rewordium is a one-time purchase. No subscriptions, no ads.

Questions? noxquilltech@gmail.com
```

## What's new (≤ 500) — version 1.0.0

```
Welcome to Rewordium – Smart Keyboard.
• A fast keyboard with intelligent autocorrect, swipe typing and next-word suggestions, worked out on your phone
• No sign-in and no subscription
• Optional AI rewriting, tone changes and summaries, with included AI uses shown in the app
• Themes, stickers, GIFs and emoji search, plus a community theme gallery
```

## Screenshot captions

1. A smarter keyboard. Autocorrect, swipe typing and suggestions.
2. Your typing stays on your phone. No account, no ads.
3. Make it yours. Theme packs, a theme builder and a community gallery.
4. Stickers, GIFs and emoji, with search.
5. Optional AI: rewrite, change the tone, summarize. Included uses shown in the app.
6. One purchase. No subscriptions.

## Store settings checklist

- Category: Tools (or Productivity). Tags: keyboard, typing.
- Privacy policy URL: https://chethan616.github.io/Rewordium/keyboard/privacy-policy.html
- Account deletion URL (the app offers optional sign-in): https://chethan616.github.io/Rewordium/keyboard/delete-data.html
- Contains ads: No. In-app purchases: No.
- Advertising ID: the app doesn't use it (permission removed, collection disabled).
- Accessibility API: declare the optional writing assistant and its purpose in the Play Console accessibility declaration.

## Data safety form (draft — check against Firebase's official Play disclosure guidance before submitting)

Data is encrypted in transit: **Yes**. Users can request deletion: **Yes** (in-app for signed-in accounts; delete-data page for everyone).

| Category › type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Personal info › Name, Email address | Yes | No | Yes | Account management | Only if the user signs in |
| App activity › App interactions | Yes | No | No | Analytics | Google Analytics for Firebase; no typed text |
| App activity › In-app search history | Yes | No | Yes | App functionality | GIF/sticker search words sent to KLIPY at the user's request |
| App activity › Other user-generated content | Yes | No | Yes | App functionality | Text the user sends to an AI tool (sent to Groq at the user's request, not stored by us); published themes; AI reply reports |
| Location › Approximate location | Yes | No | No | Analytics | Google Analytics for Firebase derives a coarse region from the IP address |
| Personal info › User IDs | Yes | No | No | App functionality, Account management | Firebase user ID (anonymous until the user signs in) |
| Messages › Other in-app messages | Yes | No | Yes | App functionality | Only when the optional writing assistant sends the visible conversation to an AI tool for reply suggestions |
| Device or other IDs | Yes | No | No | App functionality, Analytics | Firebase installation ID, Analytics app-instance ID, FCM push token |

Not collected: typed keystrokes, contacts (read on device only, when enabled), photos (on device only), precise location, advertising ID, financial info (the Play purchase is handled by Google Play).

"Shared" is **No** for the rows above because transfers to Groq and KLIPY happen at the user's request (user-initiated) and Google Firebase acts as a service provider. Review this if that changes.
