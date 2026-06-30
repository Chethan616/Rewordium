import KeyboardKit

/// Custom layout provider that overrides KeyboardKit's default layout.
/// Specifically used to remove the explicit Caps Lock action and replace it
/// with the standard Shift action, mimicking the stock iOS keyboard.
class RewordiumLayoutProvider: KeyboardLayoutProvider {
    
    let base: KeyboardLayoutProvider
    
    init(base: KeyboardLayoutProvider) {
        self.base = base
    }
    
    func keyboardLayout(for context: KeyboardContext) -> KeyboardLayout {
        let layout = base.keyboardLayout(for: context)
        
        // Map over the items to replace capsLock with standard shift
        layout.itemRows = layout.itemRows.map { row in
            row.map { item in
                var newItem = item
                if newItem.action == .capsLock {
                    newItem.action = .shift(currentCasing: context.keyboardCase)
                }
                return newItem
            }
        }
        
        return layout
    }
}
