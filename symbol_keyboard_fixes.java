/**
 * COMPREHENSIVE FIXES FOR THE NUMBER/SYMBOL PANEL
 * 
 * These fixes address:
 * 1. Double haptic feedback on keys
 * 2. Implementation of double-space for period
 * 3. Styling consistency with the main keyboard
 */

// ===== CHANGES TO MAKE =====

// 1. Make sure the addSymbolKey method is properly implemented with ACTION_UP handling
// Replace the existing addSymbolKey method with this version:

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

// 2. Find the spaceKey's onTouch listener in createSymbolsKeyboard method
// Modify the ACTION_UP case in the onTouch method to implement double-space for period:

case MotionEvent.ACTION_UP:
case MotionEvent.ACTION_CANCEL:
    v.setPressed(false);
    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
    // Cancel long press detection
    longPressHandler.removeCallbacks(longPressRunnable);
    
    if (isLongPress) {
        // Remove blur effect when done sliding
        applyKeyboardBlur(false, v);
    } else if (!isSliding) {
        // Get current time for double-space detection
        long currentTime = System.currentTimeMillis();
        
        // Check if this is a double-space (within 300ms of last space)
        if (currentTime - lastSpacebarTap < 300) {
            // Delete the last space
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.deleteSurroundingText(1, 0);
                // Add period and space
                performHapticFeedback();
                ic.commitText(". ", 1);
            }
        } else {
            // Just a single space
            commitText(" ");
        }
        
        // Update last spacebar tap time
        lastSpacebarTap = currentTime;
    }
    
    // Reset states
    isSliding = false;
    isLongPress = false;
    return true;

// 3. Make sure both emoji and ABC buttons use the same haptic approach in the symbols keyboard

// For the ABC button:
addSpecialKey(controlRow, "ABC", v -> {
    // Let switchKeyboardMode handle the haptic feedback, but don't trigger it here
    switchKeyboardMode("normal", false);
}, 0.2f, true);

// For the Emoji button:
addSpecialKey(controlRow, "😊", v -> {
    // Let switchKeyboardMode handle the haptic feedback, but don't trigger it here
    switchKeyboardMode("emoji", false);
}, 0.2f, true);

// 4. Add a helper method for changing keyboard modes with an option to suppress haptic feedback

/**
 * Switch between different keyboard modes with optional haptic feedback
 */
private void switchKeyboardMode(String mode, boolean performHaptic) {
    // Save previous mode for back button functionality
    previousKeyboardMode = currentKeyboardMode;
    currentKeyboardMode = mode;
    
    // Clean the keyboard container before recreating it
    keyboardContainer.removeAllViews();
    
    // Provide haptic feedback when switching modes if requested
    if (performHaptic) {
        performHapticFeedback();
    }
    
    // Create rows for the keyboard layout
    // ... (rest of the method implementation)
}

// Overload to maintain backward compatibility
private void switchKeyboardMode(String mode) {
    switchKeyboardMode(mode, true);
}
