// FIXES FOR DOUBLE HAPTIC FEEDBACK ISSUES

// 1. The symbol keys implementation is incomplete and needs to be fixed:
// Replace the addSymbolKey method with this corrected version:

private void addSymbolKey(LinearLayout row, final String symbol) {
    TextView key = new TextView(this);
    key.setText(symbol);
    key.setTextColor(Color.WHITE);
    key.setTextSize(22);
    key.setTypeface(Typeface.DEFAULT_BOLD);
    key.setGravity(Gravity.CENTER);
    key.setPadding(1, 24, 1, 24);
    
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    params.setMargins(6, 4, 6, 4);
    key.setLayoutParams(params);
    
    // Create a state list drawable for the background with blue highlight
    StateListDrawable states = new StateListDrawable();
    GradientDrawable pressedState = new GradientDrawable();
    pressedState.setShape(GradientDrawable.RECTANGLE);
    pressedState.setCornerRadius(8f);
    pressedState.setColor(Color.parseColor("#5A7CE2")); // Blue highlight color to match letter keys
    
    GradientDrawable defaultState = new GradientDrawable();
    defaultState.setShape(GradientDrawable.RECTANGLE);
    defaultState.setCornerRadius(8f);
    defaultState.setColor(Color.parseColor("#2A2C38")); // Default dark color to match letter keys
    
    states.addState(new int[] {android.R.attr.state_pressed}, pressedState);
    states.addState(new int[] {}, defaultState);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        key.setBackground(states);
    } else {
        key.setBackgroundDrawable(states);
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        key.setElevation(2f);
    }
    
    key.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Let the pressed state drawable handle the visual feedback
                    v.setPressed(true);
                    // Add a small scale effect
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(80)
                        .start();
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    // Directly commit text with haptic feedback to avoid double haptic
                    performHapticFeedback();
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(symbol, 1);
                    }
                    // Reset scale
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(80)
                        .start();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    // Reset scale
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(80)
                        .start();
                    return true;
            }
            return false;
        }
    });
    row.addView(key);
}

// 2. Fix the commitText method to avoid double haptic feedback:
// Replace the commitText method with this version that allows suppressing haptic feedback:

/**
 * Commit text to the current input connection with optional haptic feedback
 */
private void commitText(CharSequence text, boolean performHaptic) {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
        // Haptic feedback is optional
        if (performHaptic) {
            performHapticFeedback();
        }
        ic.commitText(text, 1);
    }
}

/**
 * Commit text to the current input connection with haptic feedback
 */
private void commitText(CharSequence text) {
    commitText(text, true);
}

// 3. Update the addKey method to avoid double haptic feedback:
// In the addKey method, modify the click listener section to:

// Set click listener for the actual key press action
key.setOnClickListener(v -> {
    try {
        // Handle text commitment
        String keyText = text;
        if (isCapsLockOn && text.length() == 1 && Character.isLetter(text.charAt(0))) {
            keyText = text.toUpperCase();
            
            // Reset shift after typing a letter if not in caps lock mode
            if (!isCapsLockMode) {
                shiftAutoResetHandler.removeCallbacksAndMessages(null);
                isCapsLockOn = false;
                updateShiftKeyAppearance();
                updateKeyCaps();
            }
        }
        // Commit text with haptic feedback (only trigger haptic here, not in commitText)
        performHapticFeedback();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(keyText, 1);
        }
    } catch (Exception e) {
        Log.e(TAG, "Error in key click handler: " + e.getMessage());
    }
});

// 4. Update the addEmojiKey method to avoid double haptic feedback:
// In the onTouch listener's ACTION_UP case, change:

case MotionEvent.ACTION_UP:
    // Commit the emoji and reset state
    v.setPressed(false);
    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
    performHapticFeedback();
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
        ic.commitText(emoji, 1);
    }
    return true;

// 5. Fix any special keys that might be triggering double haptic feedback:
// For special keys in addSpecialKey method, make sure the onClick handler is consistent:

// Set click listener with haptic feedback
key.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        // Perform haptic feedback before handling the click
        performHapticFeedback();
        if (listener != null) {
            listener.onClick(v);
        }
    }
});

// Make sure the listener doesn't also trigger haptic feedback unless needed

// 6. Ensure that the 123 key and emoji key don't trigger double haptic feedback:
// When adding these keys, modify their listeners to:

addSpecialKey(fourthRow, "123", v -> {
    // Don't perform haptic here, let the click handler in addSpecialKey do it
    switchKeyboardMode("symbols", false); 
}, 0.12f, true);

// Add emoji key with proper implementation
try {
    // Create the emoji key view
    TextView emojiKey = new TextView(this);
    // ...
    
    // Set click listener
    emojiKey.setOnClickListener(v -> {
        try {
            // Perform haptic feedback only once
            performHapticFeedback();
            // Switch to emoji keyboard
            switchKeyboardMode("emoji", false);
            // Force update the keyboard view
            updateInputViewShown();
        } catch (Exception e) {
            Log.e(TAG, "Error in emoji key: " + e.getMessage());
        }
    });
    // ...
}
