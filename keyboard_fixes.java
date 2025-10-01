// Fix 1: Modify the ABC key in symbols keyboard to avoid double haptic feedback
// Replace this in createSymbolsKeyboard():
addSpecialKey(controlRow, "ABC", v -> {
    // Don't perform haptic feedback here, let switchKeyboardMode handle it
    switchKeyboardMode("normal", false);
}, 0.2f, true);

// Fix 2: Modify the ABC key in emoji keyboard to avoid double haptic feedback
// Replace in createEmojiKeyboard():
abcButton.setOnTouchListener((v, event) -> {
    switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            v.setPressed(true);
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
            return true;
        case MotionEvent.ACTION_UP:
            v.setPressed(false);
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
            switchKeyboardMode("normal", false);
            return true;
        case MotionEvent.ACTION_CANCEL:
            v.setPressed(false);
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
            return true;
    }
    return false;
});

// Fix 3: Implement double-space to period in symbols keyboard
// Add these variables to the spacebar's OnTouchListener in createSymbolsKeyboard():
private long lastSpaceTapTime = 0;
private static final int DOUBLE_TAP_TIMEOUT = 300; // ms

// Then replace the ACTION_UP handler with this:
if (isLongPress) {
    // Remove blur effect when done sliding
    applyKeyboardBlur(false, v);
} else if (!isSliding) {
    // Check for double-tap on spacebar
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastSpaceTapTime < DOUBLE_TAP_TIMEOUT) {
        // Double-tap detected - add period, space and capitalize next letter
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // Delete the space that was just added
            ic.deleteSurroundingText(1, 0);
            // Add period and space
            commitText(". ");
            // Enable shift for next letter
            if (!isCapsLockOn) {
                toggleShift();
            }
        }
        // Reset the timer
        lastSpaceTapTime = 0;
    } else {
        // Single tap - just add a space
        commitText(" ");
        // Record the time for potential double-tap detection
        lastSpaceTapTime = currentTime;
    }
}

// Fix 4: Implement double-space to period in emoji keyboard
// Add these variables to the spacebar's OnTouchListener in createEmojiKeyboard():
private long lastSpaceTapTime = 0;
private static final int DOUBLE_TAP_TIMEOUT = 300; // ms

// Then replace the ACTION_UP handler with this:
if (isLongPress) {
    // Remove blur effect when done sliding
    applyKeyboardBlur(false, v);
} else if (!isSliding) {
    // Check for double-tap on spacebar
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastSpaceTapTime < DOUBLE_TAP_TIMEOUT) {
        // Double-tap detected - add period, space and capitalize next letter
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // Delete the space that was just added
            ic.deleteSurroundingText(1, 0);
            // Add period and space
            commitText(". ");
            // Enable shift for next letter
            if (!isCapsLockOn) {
                toggleShift();
            }
        }
        // Reset the timer
        lastSpaceTapTime = 0;
    } else {
        // Single tap - just add a space
        commitText(" ");
        // Record the time for potential double-tap detection
        lastSpaceTapTime = currentTime;
    }
}

// Fix 5: Improve the styling of ABC, spacebar, and backspace keys in emoji panel
// Replace the ABC button styling in createEmojiKeyboard():
// Create a modern styled ABC button
TextView abcButton = new TextView(this);
abcButton.setText("ABC");
abcButton.setTextColor(Color.WHITE);
abcButton.setTextSize(16);
abcButton.setTypeface(Typeface.DEFAULT_BOLD);
abcButton.setGravity(Gravity.CENTER);

// Create background states for ABC button
GradientDrawable abcBg = new GradientDrawable();
abcBg.setShape(GradientDrawable.RECTANGLE);
abcBg.setCornerRadius(10f); // More rounded corners
abcBg.setColor(Color.parseColor("#3F4152")); // Dark gray background
abcBg.setStroke(1, Color.parseColor("#666666")); // Subtle border

// Set up ripple effect for Lollipop and above
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    int[][] states = new int[][]{
        new int[]{android.R.attr.state_pressed},
        new int[]{}
    };
    
    int[] colors = new int[]{
        Color.parseColor("#4A6CD1"), // Pressed state
        Color.TRANSPARENT
    };
    
    ColorStateList rippleColor = new ColorStateList(states, colors);
    RippleDrawable ripple = new RippleDrawable(
        rippleColor,
        abcBg,
        null
    );
    abcButton.setBackground(ripple);
    abcButton.setElevation(2f);
} else {
    abcButton.setBackgroundDrawable(abcBg);
}

// Replace the spacebar styling in createEmojiKeyboard():
// Create a modern styled spacebar
TextView spaceKey = new TextView(this);
spaceKey.setText("Space");
spaceKey.setTextColor(Color.WHITE);
spaceKey.setTextSize(16);
spaceKey.setTypeface(Typeface.DEFAULT_BOLD);
spaceKey.setGravity(Gravity.CENTER);

// Create background states for spacebar
GradientDrawable spaceBg = new GradientDrawable();
spaceBg.setShape(GradientDrawable.RECTANGLE);
spaceBg.setCornerRadius(10f); // More rounded corners
spaceBg.setColor(Color.parseColor("#3F4152")); // Dark gray background
spaceBg.setStroke(1, Color.parseColor("#666666")); // Subtle border

// Set up ripple effect for Lollipop and above
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    int[][] states = new int[][]{
        new int[]{android.R.attr.state_pressed},
        new int[]{}
    };
    
    int[] colors = new int[]{
        Color.parseColor("#4A6CD1"), // Pressed state
        Color.TRANSPARENT
    };
    
    ColorStateList rippleColor = new ColorStateList(states, colors);
    RippleDrawable ripple = new RippleDrawable(
        rippleColor,
        spaceBg,
        null
    );
    spaceKey.setBackground(ripple);
    spaceKey.setElevation(2f);
} else {
    spaceKey.setBackgroundDrawable(spaceBg);
}

// Replace the backspace key styling in createEmojiKeyboard():
// Create a modern styled backspace key
TextView backspaceKey = new TextView(this);
backspaceKey.setText("⌫");
backspaceKey.setTextColor(Color.WHITE);
backspaceKey.setTextSize(20);
backspaceKey.setTypeface(Typeface.DEFAULT_BOLD);
backspaceKey.setGravity(Gravity.CENTER);

// Create background states for backspace key
GradientDrawable backspaceBg = new GradientDrawable();
backspaceBg.setShape(GradientDrawable.RECTANGLE);
backspaceBg.setCornerRadius(10f); // More rounded corners
backspaceBg.setColor(Color.parseColor("#3F4152")); // Dark gray background
backspaceBg.setStroke(1, Color.parseColor("#666666")); // Subtle border

// Set up ripple effect for Lollipop and above
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    int[][] states = new int[][]{
        new int[]{android.R.attr.state_pressed},
        new int[]{}
    };
    
    int[] colors = new int[]{
        Color.parseColor("#4A6CD1"), // Pressed state
        Color.TRANSPARENT
    };
    
    ColorStateList rippleColor = new ColorStateList(states, colors);
    RippleDrawable ripple = new RippleDrawable(
        rippleColor,
        backspaceBg,
        null
    );
    backspaceKey.setBackground(ripple);
    backspaceKey.setElevation(2f);
} else {
    backspaceKey.setBackgroundDrawable(backspaceBg);
}
