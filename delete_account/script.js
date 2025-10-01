// Firebase Configuration - Using the CORRECT project (rewordium)
const firebaseConfig = {
    // This should match your mobile app's Firebase project
    apiKey: 'AIzaSyBoG1w3GaQVy2O6kjp-ZQw09TZgbUWjrTg',
    appId: '1:1046215732414:web:rewordium_web_app',  // You may need to create a web app in Firebase console
    messagingSenderId: '1046215732414',
    projectId: 'rewordium',  // This is the correct project your app uses
    authDomain: 'rewordium.firebaseapp.com',
    storageBucket: 'rewordium.firebasestorage.app'
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();

// Application State
const AppState = {
    currentUser: null,
    isLoading: false,
    currentStep: 'signin' // 'signin', 'userinfo', 'delete'
};

// DOM Elements
const elements = {
    signInSection: document.getElementById('signInSection'),
    userInfoSection: document.getElementById('userInfoSection'),
    deleteSection: document.getElementById('deleteSection'),
    googleSignInBtn: document.getElementById('googleSignIn'),
    emailSignInBtn: document.getElementById('emailSignIn'),
    emailInput: document.getElementById('emailInput'),
    passwordInput: document.getElementById('passwordInput'),
    userAvatar: document.getElementById('userAvatar'),
    userName: document.getElementById('userName'),
    userEmail: document.getElementById('userEmail'),
    userProvider: document.getElementById('userProvider'),
    changeAccountBtn: document.getElementById('changeAccountBtn'),
    confirmInput: document.getElementById('confirmInput'),
    inputValidation: document.getElementById('inputValidation'),
    deleteButton: document.getElementById('deleteButton'),
    cancelButton: document.getElementById('cancelButton'),
    loadingOverlay: document.getElementById('loadingOverlay'),
    loadingText: document.getElementById('loadingText'),
    messageContainer: document.getElementById('messageContainer')
};

// Utility Functions
const showLoading = (show = true, text = 'Processing...') => {
    AppState.isLoading = show;
    elements.loadingOverlay.style.display = show ? 'flex' : 'none';
    elements.loadingText.textContent = text;
};

const showMessage = (message, type = 'error', duration = 5000) => {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message message-${type}`;
    
    const icon = type === 'error' ? '❌' : type === 'success' ? '✅' : '⚠️';
    messageDiv.innerHTML = `<span>${icon}</span><span>${message}</span>`;
    
    elements.messageContainer.appendChild(messageDiv);
    
    if (duration > 0) {
        setTimeout(() => {
            if (messageDiv.parentNode) {
                messageDiv.parentNode.removeChild(messageDiv);
            }
        }, duration);
    }
    
    return messageDiv;
};

const clearMessages = () => {
    elements.messageContainer.innerHTML = '';
};

const updateUI = () => {
    const { currentStep } = AppState;
    
    elements.signInSection.style.display = currentStep === 'signin' ? 'block' : 'none';
    elements.userInfoSection.style.display = currentStep === 'userinfo' || currentStep === 'delete' ? 'block' : 'none';
    elements.deleteSection.style.display = currentStep === 'delete' ? 'block' : 'none';
};

const getProviderName = (user) => {
    if (!user || !user.providerData || user.providerData.length === 0) {
        return 'Email';
    }
    
    const providerId = user.providerData[0].providerId;
    switch (providerId) {
        case 'google.com':
            return 'Google';
        case 'password':
            return 'Email';
        default:
            return 'Unknown';
    }
};

const generateAvatar = (user) => {
    if (user.photoURL) {
        return user.photoURL;
    }
    
    const name = user.displayName || user.email || 'User';
    const initials = name.charAt(0).toUpperCase();
    const colors = ['#667eea', '#764ba2', '#f093fb', '#f5576c', '#4facfe', '#00f2fe'];
    const color = colors[name.charCodeAt(0) % colors.length];
    
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(initials)}&background=${color.slice(1)}&color=fff&size=120&rounded=true&bold=true`;
};

// Authentication Functions
const signInWithGoogle = async () => {
    try {
        showLoading(true, 'Signing in with Google...');
        clearMessages();
        
        const provider = new firebase.auth.GoogleAuthProvider();
        provider.addScope('email');
        provider.addScope('profile');
        
        // Configure for better UX
        provider.setCustomParameters({
            prompt: 'select_account'
        });
        
        const result = await auth.signInWithPopup(provider);
        console.log('Google sign-in successful:', result.user);
        
    } catch (error) {
        console.error('Google sign-in error:', error);
        
        let errorMessage = 'Failed to sign in with Google.';
        
        if (error.code === 'auth/popup-closed-by-user') {
            errorMessage = 'Sign-in was canceled. Please try again.';
        } else if (error.code === 'auth/network-request-failed') {
            errorMessage = 'Network error. Please check your connection and try again.';
        } else if (error.code === 'auth/too-many-requests') {
            errorMessage = 'Too many failed attempts. Please try again later.';
        } else if (error.message) {
            errorMessage += ` ${error.message}`;
        }
        
        showMessage(errorMessage, 'error');
    } finally {
        showLoading(false);
    }
};

const signInWithEmail = async () => {
    const email = elements.emailInput.value.trim();
    const password = elements.passwordInput.value;

    if (!email || !password) {
        showMessage('Please enter both email and password.', 'error');
        elements.emailInput.focus();
        return;
    }

    if (!isValidEmail(email)) {
        showMessage('Please enter a valid email address.', 'error');
        elements.emailInput.focus();
        return;
    }

    try {
        showLoading(true, 'Signing in...');
        clearMessages();
        
        const result = await auth.signInWithEmailAndPassword(email, password);
        console.log('Email sign-in successful:', result.user);
        
    } catch (error) {
        console.error('Email sign-in error:', error);
        
        let errorMessage = 'Failed to sign in.';
        
        switch (error.code) {
            case 'auth/user-not-found':
                errorMessage = 'No account found with this email address.';
                break;
            case 'auth/wrong-password':
                errorMessage = 'Incorrect password. Please try again.';
                break;
            case 'auth/invalid-email':
                errorMessage = 'Invalid email address format.';
                break;
            case 'auth/user-disabled':
                errorMessage = 'This account has been disabled.';
                break;
            case 'auth/too-many-requests':
                errorMessage = 'Too many failed attempts. Please try again later.';
                break;
            default:
                if (error.message) {
                    errorMessage += ` ${error.message}`;
                }
        }
        
        showMessage(errorMessage, 'error');
    } finally {
        showLoading(false);
    }
};

const isValidEmail = (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};

// Account Deletion Functions
const deleteUserAccount = async () => {
    const user = auth.currentUser;
    if (!user) {
        showMessage('No user is currently signed in.', 'error');
        return;
    }

    if (elements.confirmInput.value !== 'DELETE MY ACCOUNT') {
        showMessage('Please type "DELETE MY ACCOUNT" exactly as shown.', 'error');
        elements.confirmInput.focus();
        return;
    }

    try {
        showLoading(true, 'Deleting your account...');
        clearMessages();
        
        // Step 1: Delete user data from Firestore
        try {
            showLoading(true, 'Removing your data...');
            
            // Delete user document
            await db.collection('users').doc(user.uid).delete();
            console.log('User document deleted from Firestore');
            
            // Delete user's subcollections (if any)
            const collections = ['documents', 'settings', 'usage_stats', 'preferences'];
            
            for (const collectionName of collections) {
                try {
                    const subcollectionRef = db.collection('users').doc(user.uid).collection(collectionName);
                    const snapshot = await subcollectionRef.get();
                    
                    const batch = db.batch();
                    snapshot.docs.forEach((doc) => {
                        batch.delete(doc.ref);
                    });
                    
                    if (snapshot.docs.length > 0) {
                        await batch.commit();
                        console.log(`Deleted ${snapshot.docs.length} documents from ${collectionName}`);
                    }
                } catch (subcollectionError) {
                    console.warn(`Failed to delete ${collectionName}:`, subcollectionError);
                    // Continue with deletion even if some subcollections fail
                }
            }
            
        } catch (firestoreError) {
            console.error('Error deleting Firestore data:', firestoreError);
            showMessage('Warning: Some data may not have been completely removed, but account deletion will continue.', 'warning', 3000);
        }

        // Step 2: Delete the user account
        showLoading(true, 'Finalizing account deletion...');
        await user.delete();
        
        // Step 3: Show success message
        clearMessages();
        showMessage('✅ Account deleted successfully! You will be redirected shortly.', 'success', 0);
        
        // Step 4: Redirect after delay
        setTimeout(() => {
            window.location.href = 'https://rewordium.com'; // Change to your website URL
        }, 3000);

    } catch (error) {
        console.error('Error deleting account:', error);
        
        let errorMessage = 'Failed to delete account.';
        
        if (error.code === 'auth/requires-recent-login') {
            errorMessage = 'For security reasons, you need to sign in again before deleting your account.';
            showMessage(errorMessage, 'error');
            
            setTimeout(() => {
                auth.signOut();
            }, 2000);
        } else {
            switch (error.code) {
                case 'auth/network-request-failed':
                    errorMessage = 'Network error. Please check your connection and try again.';
                    break;
                case 'auth/too-many-requests':
                    errorMessage = 'Too many requests. Please wait a moment and try again.';
                    break;
                default:
                    if (error.message) {
                        errorMessage += ` ${error.message}`;
                    }
            }
            
            showMessage(errorMessage, 'error');
        }
    } finally {
        showLoading(false);
    }
};

// Event Listeners
elements.googleSignInBtn.addEventListener('click', signInWithGoogle);
elements.emailSignInBtn.addEventListener('click', signInWithEmail);

elements.changeAccountBtn.addEventListener('click', () => {
    auth.signOut();
});

elements.cancelButton.addEventListener('click', () => {
    AppState.currentStep = 'userinfo';
    updateUI();
    elements.confirmInput.value = '';
    elements.inputValidation.textContent = '';
    elements.deleteButton.disabled = true;
    clearMessages();
});

elements.deleteButton.addEventListener('click', deleteUserAccount);

// Input validation for confirmation
elements.confirmInput.addEventListener('input', () => {
    const value = elements.confirmInput.value;
    const isValid = value === 'DELETE MY ACCOUNT';
    
    elements.deleteButton.disabled = !isValid;
    
    if (value.length === 0) {
        elements.inputValidation.textContent = '';
        elements.inputValidation.className = 'input-validation';
    } else if (isValid) {
        elements.inputValidation.textContent = '✓ Confirmation text matches';
        elements.inputValidation.className = 'input-validation valid';
    } else {
        elements.inputValidation.textContent = '✗ Please type exactly: DELETE MY ACCOUNT';
        elements.inputValidation.className = 'input-validation invalid';
    }
});

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (AppState.currentStep === 'delete') {
            elements.cancelButton.click();
        }
    }
});

elements.emailInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        elements.passwordInput.focus();
    }
});

elements.passwordInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        elements.emailSignInBtn.click();
    }
});

elements.confirmInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter' && !elements.deleteButton.disabled) {
        elements.deleteButton.click();
    }
});

// Auth state observer
auth.onAuthStateChanged((user) => {
    AppState.currentUser = user;
    
    if (user) {
        // User is signed in - show user info
        AppState.currentStep = 'userinfo';
        
        elements.userName.textContent = user.displayName || 'User';
        elements.userEmail.textContent = user.email || 'No email';
        elements.userProvider.textContent = `Signed in via ${getProviderName(user)}`;
        elements.userAvatar.src = generateAvatar(user);
        
        // Handle avatar load error
        elements.userAvatar.onerror = () => {
            elements.userAvatar.src = generateAvatar(user);
        };
        
        console.log('User signed in:', user);
        
    } else {
        // User is signed out - show sign in
        AppState.currentStep = 'signin';
        
        // Reset form
        elements.emailInput.value = '';
        elements.passwordInput.value = '';
        elements.confirmInput.value = '';
        elements.inputValidation.textContent = '';
        elements.deleteButton.disabled = true;
        
        console.log('User signed out');
    }
    
    updateUI();
    showLoading(false);
});

// Handle user info section clicks
elements.userInfoSection.addEventListener('click', (e) => {
    if (e.target.closest('.user-card') && AppState.currentStep === 'userinfo') {
        AppState.currentStep = 'delete';
        updateUI();
        clearMessages();
        
        // Focus on confirmation input
        setTimeout(() => {
            elements.confirmInput.focus();
        }, 100);
    }
});

// Initialize the app
document.addEventListener('DOMContentLoaded', () => {
    console.log('Account deletion portal initialized');
    
    // Show initial loading
    showLoading(true, 'Initializing...');
    
    // The auth state observer will handle the initial state
});
