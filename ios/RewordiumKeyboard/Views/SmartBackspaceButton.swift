import SwiftUI
import KeyboardKit

/// Replaces the default backspace button to add two premium features:
/// 1. Swipe left to delete word-by-word.
/// 2. Accelerated (turbo) delete when holding the button.
struct SmartBackspaceButton<Content: View>: View {
    let controller: KeyboardInputViewController
    let defaultView: Content
    
    // Swipe state
    @State private var wordsDeletedInSwipe: Int = 0
    @State private var initialSwipeText: String? = nil
    
    // Turbo delete state
    @State private var turboTimer: Timer?
    @State private var turboSpeed: TimeInterval = 0.1
    @State private var isPressed: Bool = false
    
    var body: some View {
        defaultView
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !isPressed {
                            isPressed = true
                            startTurboDelete()
                        }
                        handleSwipe(translation: value.translation.width)
                    }
                    .onEnded { _ in
                        isPressed = false
                        stopTurboDelete()
                        wordsDeletedInSwipe = 0
                        initialSwipeText = nil
                    }
            )
    }
    
    // MARK: - Swipe Delete
    
    private func handleSwipe(translation: CGFloat) {
        if translation < -10 {
            stopTurboDelete()
        }
        guard translation < -20 else { return }
        
        // Delete a word for every 35 points of leftward swipe
        let targetWordCount = Int(-translation / 35)
        
        if targetWordCount > wordsDeletedInSwipe {
            let wordsToDelete = targetWordCount - wordsDeletedInSwipe
            for _ in 0..<wordsToDelete {
                deleteWord()
            }
            wordsDeletedInSwipe = targetWordCount
        }
    }
    
    // MARK: - Turbo Delete
    
    private func startTurboDelete() {
        stopTurboDelete()
        turboSpeed = 0.12 // Initial repeat speed
        
        // Single tap immediately
        controller.textDocumentProxy.deleteBackward()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        
        // Wait 0.4s before repeating (standard iOS key repeat delay)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            guard self.isPressed else { return }
            self.fireTurboTimer()
        }
    }
    
    private func fireTurboTimer() {
        guard isPressed else { return }
        
        controller.textDocumentProxy.deleteBackward()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        
        // Accelerate! Cap at 0.02s per deletion for "turbo" mode
        turboSpeed = max(0.02, turboSpeed * 0.85)
        
        turboTimer = Timer.scheduledTimer(withTimeInterval: turboSpeed, repeats: false) { _ in
            self.fireTurboTimer()
        }
    }
    
    private func stopTurboDelete() {
        turboTimer?.invalidate()
        turboTimer = nil
    }
    
    // MARK: - Helpers
    
    private func deleteWord() {
        guard let text = controller.textDocumentProxy.documentContextBeforeInput, !text.isEmpty else {
            controller.textDocumentProxy.deleteBackward()
            return
        }
        
        var charsToDelete = 0
        var foundChar = false
        
        for char in text.reversed() {
            if char.isWhitespace || char.isPunctuation {
                if foundChar { break }
                charsToDelete += 1
            } else {
                foundChar = true
                charsToDelete += 1
            }
        }
        
        if charsToDelete == 0 {
            controller.textDocumentProxy.deleteBackward()
        } else {
            for _ in 0..<charsToDelete {
                controller.textDocumentProxy.deleteBackward()
            }
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }
}
