package com.example.yc_startup.keyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Helper class to provide key animation effects for the keyboard.
 * Creates a visual effect where keys appear to rise up and get larger when pressed.
 */
public class KeyAnimator {
    
    // Constants for animation properties
    private static final float SCALE_FACTOR = 1.45f; // 45% larger for dramatically more pronounced effect
    private static final float TRANSLATION_UP = -8f; // Move up significantly for better visual pop
    
    // Constants for spacebar-specific animation properties
    private static final float SPACEBAR_SCALE_FACTOR = 1.05f; // 5% larger for subtle spacebar effect
    private static final float SPACEBAR_TRANSLATION_UP = -12f; // Move up more significantly for spacebar
    private static final int PRESS_DURATION = 50; // Fast press animation (ms)
    private static final int RELEASE_DURATION = 150; // Slower release for spring effect (ms)
    
    /**
     * Animates a key when pressed/released to create a pop-up effect
     * 
     * @param view The key view to animate
     * @param isPressed True if key is being pressed, false if being released
     */
    public static void animateKey(View view, boolean isPressed) {
        if (view == null) return;
        
        // Cancel any existing animations
        view.animate().cancel();
        
        if (isPressed) {
            // Key press animation - scale up and move up
            AnimatorSet pressAnimator = new AnimatorSet();
            
            // Scale animations
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, SCALE_FACTOR);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, SCALE_FACTOR);
            
            // Movement animation (slight upward)
            ObjectAnimator moveUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, TRANSLATION_UP);
            
            // Combine and configure animations
            pressAnimator.playTogether(scaleX, scaleY, moveUp);
            pressAnimator.setDuration(PRESS_DURATION);
            pressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            
            // Start animations
            pressAnimator.start();
            
            // Also darken slightly
            view.setAlpha(0.9f);
            
        } else {
            // Key release animation - return to normal
            AnimatorSet releaseAnimator = new AnimatorSet();
            
            // Scale animations
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, SCALE_FACTOR, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, SCALE_FACTOR, 1.0f);
            
            // Movement animation (back down)
            ObjectAnimator moveDown = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, TRANSLATION_UP, 0f);
            
            // Alpha restore
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1.0f);
            
            // Combine and configure animations
            releaseAnimator.playTogether(scaleX, scaleY, moveDown, fadeIn);
            releaseAnimator.setDuration(RELEASE_DURATION);
            releaseAnimator.setInterpolator(new DecelerateInterpolator());
            
            // Start animations
            releaseAnimator.start();
        }
    }
    
    /**
     * Animates the spacebar with a more dramatic effect when pressed/released
     * 
     * @param view The spacebar view to animate
     * @param isPressed True if spacebar is being pressed, false if being released
     */
    public static void animateSpacebar(View view, boolean isPressed) {
        if (view == null) return;
        
        // Cancel any existing animations
        view.animate().cancel();
        
        if (isPressed) {
            // Spacebar press animation - scale up more than regular keys
            AnimatorSet pressAnimator = new AnimatorSet();
            
            // Scale animations - larger than regular keys
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, SPACEBAR_SCALE_FACTOR);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, SPACEBAR_SCALE_FACTOR);
            
            // Movement animation (bigger upward movement)
            ObjectAnimator moveUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, SPACEBAR_TRANSLATION_UP);
            
            // Combine and configure animations
            pressAnimator.playTogether(scaleX, scaleY, moveUp);
            pressAnimator.setDuration(PRESS_DURATION);
            pressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            
            // Start animations
            pressAnimator.start();
            
            // Also darken slightly
            view.setAlpha(0.9f);
            
        } else {
            // Spacebar release animation - return to normal
            AnimatorSet releaseAnimator = new AnimatorSet();
            
            // Scale animations
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, SPACEBAR_SCALE_FACTOR, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, SPACEBAR_SCALE_FACTOR, 1.0f);
            
            // Movement animation (back down)
            ObjectAnimator moveDown = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, SPACEBAR_TRANSLATION_UP, 0f);
            
            // Alpha restore
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1.0f);
            
            // Combine and configure animations
            releaseAnimator.playTogether(scaleX, scaleY, moveDown, fadeIn);
            releaseAnimator.setDuration(RELEASE_DURATION);
            releaseAnimator.setInterpolator(new DecelerateInterpolator());
            
            // Start animations
            releaseAnimator.start();
        }
    }
}
