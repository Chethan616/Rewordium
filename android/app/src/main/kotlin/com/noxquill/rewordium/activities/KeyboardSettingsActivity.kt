package com.noxquill.rewordium.activities

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.noxquill.rewordium.keyboard.util.KeyboardConstants

class KeyboardSettingsActivity : Activity() {
    
    companion object {
        private const val TAG = "KeyboardSettingsActivity"
    }
    
    private lateinit var prefs: SharedPreferences
    private var isDarkMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the activity transparent with blur effect
        setupTransparentWindow()
        
        // Get dark mode from intent
        isDarkMode = intent.getBooleanExtra("isDarkMode", false)
        
        // Initialize preferences
        prefs = getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Create the settings UI
        createSettingsUI()
        
        Log.d(TAG, "Keyboard settings activity created with isDarkMode: $isDarkMode")
    }
    
    private fun setupTransparentWindow() {
        // Make the activity fullscreen and transparent
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Set transparent background
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // Enable blur effect
        window.setFlags(
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        )
        
        // Make the activity appear as overlay
        window.setFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
        window.attributes.dimAmount = 0.6f
    }
    
    private fun createSettingsUI() {
        // Create main container
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }
        
        // Create settings panel
        val settingsPanel = createSettingsPanel()
        mainContainer.addView(settingsPanel)
        
        // Set up click outside to close
        mainContainer.setOnClickListener {
            finish()
        }
        
        // Prevent closing when clicking inside the panel
        settingsPanel.setOnClickListener { /* Do nothing to prevent closing */ }
        
        setContentView(mainContainer)
    }
    
    private fun createSettingsPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            
            // Create rounded background with blur effect
            val drawable = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(if (isDarkMode) 
                    Color.argb(240, 20, 20, 20) else 
                    Color.argb(250, 255, 255, 255)
                )
                setStroke(2, if (isDarkMode) 
                    Color.argb(60, 255, 255, 255) else 
                    Color.argb(40, 0, 0, 0)
                )
            }
            background = drawable
            
            setPadding(32, 32, 32, 32)
            elevation = 16f
        }
        
        // Header with settings icon
        val header = TextView(this).apply {
            text = "⚙️ Keyboard Settings"
            textSize = 20f
            setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        panel.addView(header)
        
        // Add a subtle divider
        val divider = View(this).apply {
            setBackgroundColor(if (isDarkMode) 
                Color.argb(40, 255, 255, 255) else 
                Color.argb(20, 0, 0, 0)
            )
        }
        panel.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2
        ).apply { bottomMargin = 24 })
        
        // Settings options
        panel.addView(createSettingSwitch(
            "🔄 Haptic Feedback",
            "Feel vibration when typing",
            KeyboardConstants.KEY_HAPTIC_FEEDBACK,
            true
        ))
        
        panel.addView(createSettingSwitch(
            "🔤 Auto-Capitalize",
            "Capitalize sentences automatically",
            KeyboardConstants.KEY_AUTO_CAPITALIZE,
            true
        ))
        
        panel.addView(createSettingSwitch(
            "⏺ Double-Space Period",
            "Add period with double space",
            KeyboardConstants.KEY_DOUBLE_SPACE_PERIOD,
            true
        ))
        
        // Close button with better styling
        val closeButton = TextView(this).apply {
            text = "✖ Close"
            textSize = 16f
            setTextColor(Color.WHITE)
            
            val buttonDrawable = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.argb(255, 0, 122, 255))
            }
            background = buttonDrawable
            
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            setOnClickListener { finish() }
        }
        panel.addView(closeButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 24
        })
        
        return panel
    }
    
    private fun createSettingSwitch(
        title: String,
        subtitle: String,
        prefKey: String,
        defaultValue: Boolean
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            
            // Add subtle background for each setting
            val drawable = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (isDarkMode) 
                    Color.argb(30, 255, 255, 255) else 
                    Color.argb(20, 0, 0, 0)
                )
            }
            background = drawable
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val subtitleText = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(if (isDarkMode) Color.LTGRAY else Color.GRAY)
            setPadding(0, 4, 0, 0)
        }
        
        textContainer.addView(titleText)
        textContainer.addView(subtitleText)
        
        val switch = Switch(this).apply {
            isChecked = prefs.getBoolean(prefKey, defaultValue)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(prefKey, isChecked).apply()
                Log.d(TAG, "Setting $prefKey changed to: $isChecked")
            }
        }
        
        container.addView(textContainer)
        container.addView(switch)
        
        // Add margin between settings
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 12
        }
        container.layoutParams = params
        
        return container
    }
    
    override fun finish() {
        super.finish()
        // Add smooth exit animation
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
