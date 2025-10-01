// Debug version - Testing Firebase connection
console.log('Debug script loaded');

// Simplified configuration for testing
const rewordiumConfig = {
    apiKey: 'AIzaSyBoG1w3GaQVy2O6kjp-ZQw09TZgbUWjrTg',
    authDomain: 'rewordium.firebaseapp.com',
    projectId: 'rewordium',
    storageBucket: 'rewordium.firebasestorage.app',
    messagingSenderId: '1046215732414'
};

console.log('Firebase config:', rewordiumConfig);

// Initialize Firebase
let app, auth, db;
try {
    app = firebase.initializeApp(rewordiumConfig);
    auth = firebase.auth(app);
    db = firebase.firestore(app);
    console.log('Firebase initialized successfully');
} catch (error) {
    console.error('Firebase initialization error:', error);
}

// DOM Elements
const googleSignInBtn = document.getElementById('googleSignIn');
const emailSignInBtn = document.getElementById('emailSignIn');
const emailInput = document.getElementById('emailInput');
const passwordInput = document.getElementById('passwordInput');

// Test Google Sign-In
const testGoogleSignIn = async () => {
    console.log('Testing Google Sign-In...');
    
    try {
        const provider = new firebase.auth.GoogleAuthProvider();
        provider.addScope('email');
        provider.addScope('profile');
        
        console.log('Created Google provider');
        
        const result = await auth.signInWithPopup(provider);
        console.log('Sign-in successful:', result.user);
        
        // Show user info
        document.getElementById('userName').textContent = result.user.displayName || 'User';
        document.getElementById('userEmail').textContent = result.user.email || 'No email';
        
    } catch (error) {
        console.error('Google sign-in error:', error);
        console.error('Error code:', error.code);
        console.error('Error message:', error.message);
    }
};

// Test Email Sign-In
const testEmailSignIn = async () => {
    const email = emailInput.value.trim();
    const password = passwordInput.value;
    
    console.log('Testing email sign-in for:', email);
    
    if (!email || !password) {
        console.log('Missing email or password');
        return;
    }
    
    try {
        const result = await auth.signInWithEmailAndPassword(email, password);
        console.log('Email sign-in successful:', result.user);
    } catch (error) {
        console.error('Email sign-in error:', error);
        console.error('Error code:', error.code);
        console.error('Error message:', error.message);
    }
};

// Show loading overlay
const showLoading = (show = true, text = 'Processing...') => {
    const loadingOverlay = document.getElementById('loadingOverlay');
    const loadingText = document.getElementById('loadingText');
    if (loadingOverlay) {
        loadingOverlay.style.display = show ? 'flex' : 'none';
    }
    if (loadingText) {
        loadingText.textContent = text;
    }
};

// Show message function
const showMessage = (message, type = 'error') => {
    const messageContainer = document.getElementById('messageContainer');
    if (!messageContainer) return;
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `message message-${type}`;
    
    const icon = type === 'error' ? '❌' : type === 'success' ? '✅' : '⚠️';
    messageDiv.innerHTML = `<span>${icon}</span><span>${message}</span>`;
    
    messageContainer.appendChild(messageDiv);
    
    setTimeout(() => {
        if (messageDiv.parentNode) {
            messageDiv.parentNode.removeChild(messageDiv);
        }
    }, 5000);
};

// Simple account deletion function
const deleteAccount = async () => {
    const confirmInput = document.getElementById('confirmInput');
    if (!confirmInput || confirmInput.value !== 'DELETE MY ACCOUNT') {
        showMessage('Please type "DELETE MY ACCOUNT" exactly as shown.', 'error');
        confirmInput.focus();
        return;
    }
    
    const user = auth.currentUser;
    if (!user) {
        showMessage('No user is currently signed in.', 'error');
        return;
    }
    
    // Show confirmation dialog
    if (!confirm('Are you absolutely sure you want to delete your account? This action cannot be undone!')) {
        return;
    }
    
    try {
        showLoading(true, 'Deleting your account...');
        console.log('Deleting account for:', user.email);
        
        // Delete user data from Firestore if needed
        try {
            const userDoc = db.collection('users').doc(user.uid);
            await userDoc.delete();
            console.log('User data deleted from Firestore');
        } catch (firestoreError) {
            console.warn('Could not delete Firestore data:', firestoreError);
        }
        
        // Delete the user account
        await user.delete();
        console.log('Account deleted successfully');
        
        showLoading(false);
        showMessage('✅ Account successfully deleted! You will be redirected shortly.', 'success');
        
        // Redirect after 3 seconds
        setTimeout(() => {
            window.location.href = 'https://rewordium.com';
        }, 3000);
        
    } catch (error) {
        console.error('Account deletion error:', error);
        showLoading(false);
        
        let errorMessage = 'Failed to delete account.';
        if (error.code === 'auth/requires-recent-login') {
            errorMessage = 'For security reasons, you need to sign in again before deleting your account.';
            setTimeout(() => auth.signOut(), 2000);
        } else if (error.message) {
            errorMessage += ` ${error.message}`;
        }
        
        showMessage(errorMessage, 'error');
    }
};

// Auth state observer
auth.onAuthStateChanged((user) => {
    console.log('Auth state changed:', user);
    if (user) {
        console.log('User signed in:', user.email);
        // Show user info
        document.getElementById('userName').textContent = user.displayName || 'User';
        document.getElementById('userEmail').textContent = user.email || 'No email';
        document.getElementById('userProvider').textContent = 'Signed in via Google';
        
        // Show user info section, hide others
        document.getElementById('signInSection').style.display = 'none';
        document.getElementById('userInfoSection').style.display = 'block';
        document.getElementById('deleteSection').style.display = 'none';
        
        // Clear any previous state
        const confirmInput = document.getElementById('confirmInput');
        const deleteButton = document.getElementById('deleteButton');
        if (confirmInput) confirmInput.value = '';
        if (deleteButton) deleteButton.disabled = true;
        
        // Clear messages
        const messageContainer = document.getElementById('messageContainer');
        if (messageContainer) messageContainer.innerHTML = '';
        
    } else {
        console.log('User signed out');
        // Reset to initial state
        document.getElementById('signInSection').style.display = 'block';
        document.getElementById('userInfoSection').style.display = 'none';
        document.getElementById('deleteSection').style.display = 'none';
        
        // Clear form inputs
        if (emailInput) emailInput.value = '';
        if (passwordInput) passwordInput.value = '';
        
        const confirmInput = document.getElementById('confirmInput');
        const deleteButton = document.getElementById('deleteButton');
        if (confirmInput) confirmInput.value = '';
        if (deleteButton) deleteButton.disabled = true;
        
        // Clear messages
        const messageContainer = document.getElementById('messageContainer');
        if (messageContainer) messageContainer.innerHTML = '';
    }
});

// Event listeners
if (googleSignInBtn) {
    googleSignInBtn.addEventListener('click', testGoogleSignIn);
    console.log('Google sign-in button event listener added');
}

if (emailSignInBtn) {
    emailSignInBtn.addEventListener('click', testEmailSignIn);
    console.log('Email sign-in button event listener added');
}

// Add click to proceed to delete step
document.addEventListener('click', (e) => {
    if (e.target.closest('.user-card-text')) {
        console.log('User clicked user info, showing delete section');
        // Hide sign in section
        document.getElementById('signInSection').style.display = 'none';
        // Keep user info visible but smaller
        document.getElementById('userInfoSection').style.display = 'block';
        // Show delete section
        document.getElementById('deleteSection').style.display = 'block';
        
        // Focus on confirmation input
        setTimeout(() => {
            const confirmInput = document.getElementById('confirmInput');
            if (confirmInput) confirmInput.focus();
        }, 100);
    }
});

// Add delete button listener
const deleteButton = document.getElementById('deleteButton');
if (deleteButton) {
    deleteButton.addEventListener('click', deleteAccount);
}

// Add cancel button functionality
const cancelButton = document.getElementById('cancelButton');
if (cancelButton) {
    cancelButton.addEventListener('click', () => {
        console.log('User cancelled deletion, going back to user info');
        // Hide delete section
        document.getElementById('deleteSection').style.display = 'none';
        // Show user info section only
        document.getElementById('userInfoSection').style.display = 'block';
        document.getElementById('signInSection').style.display = 'none';
        
        // Clear confirmation input
        const confirmInput = document.getElementById('confirmInput');
        if (confirmInput) {
            confirmInput.value = '';
        }
        
        // Disable delete button
        if (deleteButton) {
            deleteButton.disabled = true;
        }
        
        // Clear any messages
        const messageContainer = document.getElementById('messageContainer');
        if (messageContainer) {
            messageContainer.innerHTML = '';
        }
    });
}

// Add confirmation input validation with visual feedback
const confirmInput = document.getElementById('confirmInput');
if (confirmInput) {
    confirmInput.addEventListener('input', () => {
        const isValid = confirmInput.value === 'DELETE MY ACCOUNT';
        const validation = document.getElementById('inputValidation');
        
        if (deleteButton) {
            deleteButton.disabled = !isValid;
        }
        
        if (validation) {
            if (confirmInput.value.length === 0) {
                validation.textContent = '';
                validation.className = 'input-validation';
            } else if (isValid) {
                validation.textContent = '✓ Confirmation text matches';
                validation.className = 'input-validation valid';
            } else {
                validation.textContent = '✗ Please type exactly: DELETE MY ACCOUNT';
                validation.className = 'input-validation invalid';
            }
        }
    });
    
    // Add Enter key support
    confirmInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && deleteButton && !deleteButton.disabled) {
            deleteButton.click();
        }
    });
}

// Add change account button
const changeAccountBtn = document.getElementById('changeAccountBtn');
if (changeAccountBtn) {
    changeAccountBtn.addEventListener('click', () => {
        console.log('Signing out and returning to sign-in screen...');
        
        // Show loading briefly
        showLoading(true, 'Signing out...');
        
        setTimeout(() => {
            auth.signOut();
            showLoading(false);
        }, 500);
    });
}

document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM loaded, debug script ready');
});
