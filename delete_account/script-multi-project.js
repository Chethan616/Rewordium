// Multi-Project Firebase Configuration for Account Deletion
// This script handles accounts that might exist in either Firebase project

// Project 1: rewordium (Primary - used by mobile app)
const rewordiumConfig = {
    apiKey: 'AIzaSyBoG1w3GaQVy2O6kjp-ZQw09TZgbUWjrTg',
    authDomain: 'rewordium.firebaseapp.com',
    projectId: 'rewordium',
    storageBucket: 'rewordium.firebasestorage.app',
    messagingSenderId: '1046215732414',
    // Note: We'll use the general web config without a specific appId to start
};

// Project 2: yc-startup-yc (Secondary - might have old accounts)
const ycStartupConfig = {
    apiKey: 'AIzaSyDMVe43ZwiW9bGGEzcCVnof-kclVSP5swM',
    appId: '1:764897323980:web:7d6b2467c9053de4784b69',
    messagingSenderId: '764897323980',
    projectId: 'yc-startup-yc',
    authDomain: 'yc-startup-yc.firebaseapp.com',
    storageBucket: 'yc-startup-yc.firebasestorage.app'
};

// Initialize primary Firebase app (rewordium)
const primaryApp = firebase.initializeApp(rewordiumConfig);
const primaryAuth = firebase.auth(primaryApp);
const primaryDb = firebase.firestore(primaryApp);

// Initialize secondary Firebase app (yc-startup-yc)
const secondaryApp = firebase.initializeApp(ycStartupConfig, 'secondary');
const secondaryAuth = firebase.auth(secondaryApp);
const secondaryDb = firebase.firestore(secondaryApp);

// Application State
const AppState = {
    currentUser: null,
    currentProject: null, // 'primary' or 'secondary'
    isLoading: false,
    currentStep: 'signin' // 'signin', 'userinfo', 'delete'
};

// DOM Elements (same as before)
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

// Utility Functions (same as before)
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
    
    // Show which project the user is signed into
    if (AppState.currentUser && AppState.currentProject) {
        const projectName = AppState.currentProject === 'primary' ? 'Rewordium' : 'YC Startup';
        elements.userProvider.textContent = `${getProviderName(AppState.currentUser)} • ${projectName} Project`;
    }
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

// Enhanced Authentication Functions
const trySignInWithGoogle = async (auth, authType) => {
    const provider = new firebase.auth.GoogleAuthProvider();
    provider.addScope('email');
    provider.addScope('profile');
    provider.setCustomParameters({
        prompt: 'select_account'
    });
    
    try {
        const result = await auth.signInWithPopup(provider);
        console.log(`Google sign-in successful on ${authType}:`, result.user);
        return { success: true, user: result.user, project: authType };
    } catch (error) {
        console.log(`Google sign-in failed on ${authType}:`, error.code);
        return { success: false, error };
    }
};

const signInWithGoogle = async () => {
    try {
        showLoading(true, 'Signing in with Google...');
        clearMessages();
        
        // Try primary project first (rewordium)
        showLoading(true, 'Checking Rewordium project...');
        let result = await trySignInWithGoogle(primaryAuth, 'primary');
        
        if (result.success) {
            AppState.currentUser = result.user;
            AppState.currentProject = 'primary';
            return;
        }
        
        // If primary fails, try secondary project (yc-startup-yc)
        showLoading(true, 'Checking YC Startup project...');
        result = await trySignInWithGoogle(secondaryAuth, 'secondary');
        
        if (result.success) {
            AppState.currentUser = result.user;
            AppState.currentProject = 'secondary';
            return;
        }
        
        // Both failed
        throw result.error || new Error('No account found in either project');
        
    } catch (error) {
        console.error('Google sign-in error:', error);
        
        let errorMessage = 'Failed to sign in with Google.';
        
        if (error.code === 'auth/popup-closed-by-user') {
            errorMessage = 'Sign-in was canceled. Please try again.';
        } else if (error.code === 'auth/network-request-failed') {
            errorMessage = 'Network error. Please check your connection and try again.';
        } else if (error.code === 'auth/account-exists-with-different-credential') {
            errorMessage = 'An account already exists with this email using a different sign-in method.';
        } else if (error.message) {
            errorMessage += ` ${error.message}`;
        }
        
        showMessage(errorMessage, 'error');
    } finally {
        showLoading(false);
    }
};

const trySignInWithEmail = async (auth, email, password, authType) => {
    try {
        const result = await auth.signInWithEmailAndPassword(email, password);
        console.log(`Email sign-in successful on ${authType}:`, result.user);
        return { success: true, user: result.user, project: authType };
    } catch (error) {
        console.log(`Email sign-in failed on ${authType}:`, error.code);
        return { success: false, error };
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
        
        // Try primary project first (rewordium)
        showLoading(true, 'Checking Rewordium project...');
        let result = await trySignInWithEmail(primaryAuth, email, password, 'primary');
        
        if (result.success) {
            AppState.currentUser = result.user;
            AppState.currentProject = 'primary';
            return;
        }
        
        // If primary fails with user-not-found, try secondary
        if (result.error.code === 'auth/user-not-found') {
            showLoading(true, 'Checking YC Startup project...');
            result = await trySignInWithEmail(secondaryAuth, email, password, 'secondary');
            
            if (result.success) {
                AppState.currentUser = result.user;
                AppState.currentProject = 'secondary';
                return;
            }
        }
        
        // Handle the error from the last attempt
        const error = result.error;
        let errorMessage = 'Failed to sign in.';
        
        switch (error.code) {
            case 'auth/user-not-found':
                errorMessage = 'No account found with this email address in either project.';
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
        
    } catch (error) {
        console.error('Unexpected error:', error);
        showMessage('An unexpected error occurred. Please try again.', 'error');
    } finally {
        showLoading(false);
    }
};

const isValidEmail = (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};

// Enhanced Account Deletion Function
const deleteUserAccount = async () => {
    const user = AppState.currentUser;
    const project = AppState.currentProject;
    
    if (!user || !project) {
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
        
        const auth = project === 'primary' ? primaryAuth : secondaryAuth;
        const db = project === 'primary' ? primaryDb : secondaryDb;
        const projectName = project === 'primary' ? 'Rewordium' : 'YC Startup';
        
        // Step 1: Delete user data from Firestore
        try {
            showLoading(true, `Removing your data from ${projectName}...`);
            
            // Delete user document
            await db.collection('users').doc(user.uid).delete();
            console.log(`User document deleted from ${projectName} Firestore`);
            
            // Delete user's subcollections
            const collections = ['documents', 'settings', 'usage_stats', 'preferences', 'writing_history'];
            
            for (const collectionName of collections) {
                try {
                    const subcollectionRef = db.collection('users').doc(user.uid).collection(collectionName);
                    const snapshot = await subcollectionRef.get();
                    
                    if (snapshot.docs.length > 0) {
                        const batch = db.batch();
                        snapshot.docs.forEach((doc) => {
                            batch.delete(doc.ref);
                        });
                        
                        await batch.commit();
                        console.log(`Deleted ${snapshot.docs.length} documents from ${collectionName} in ${projectName}`);
                    }
                } catch (subcollectionError) {
                    console.warn(`Failed to delete ${collectionName} in ${projectName}:`, subcollectionError);
                }
            }
            
        } catch (firestoreError) {
            console.error(`Error deleting ${projectName} Firestore data:`, firestoreError);
            showMessage(`Warning: Some data may not have been completely removed from ${projectName}, but account deletion will continue.`, 'warning', 3000);
        }

        // Step 2: Delete the user account
        showLoading(true, 'Finalizing account deletion...');
        await user.delete();
        
        // Step 3: Show success message
        clearMessages();
        showMessage(`✅ Account successfully deleted from ${projectName}! You will be redirected shortly.`, 'success', 0);
        
        // Step 4: Reset state
        AppState.currentUser = null;
        AppState.currentProject = null;
        
        // Step 5: Redirect after delay
        setTimeout(() => {
            window.location.href = 'https://rewordium.com';
        }, 3000);

    } catch (error) {
        console.error('Error deleting account:', error);
        
        let errorMessage = 'Failed to delete account.';
        
        if (error.code === 'auth/requires-recent-login') {
            errorMessage = 'For security reasons, you need to sign in again before deleting your account.';
            showMessage(errorMessage, 'error');
            
            setTimeout(() => {
                if (AppState.currentProject === 'primary') {
                    primaryAuth.signOut();
                } else {
                    secondaryAuth.signOut();
                }
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

// Event Listeners (same as before)
elements.googleSignInBtn.addEventListener('click', signInWithGoogle);
elements.emailSignInBtn.addEventListener('click', signInWithEmail);

elements.changeAccountBtn.addEventListener('click', () => {
    if (AppState.currentProject === 'primary') {
        primaryAuth.signOut();
    } else {
        secondaryAuth.signOut();
    }
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

// Auth state observers for both projects
primaryAuth.onAuthStateChanged((user) => {
    if (user && AppState.currentProject === 'primary') {
        handleUserSignIn(user, 'primary');
    } else if (!user && AppState.currentProject === 'primary') {
        handleUserSignOut();
    }
});

secondaryAuth.onAuthStateChanged((user) => {
    if (user && AppState.currentProject === 'secondary') {
        handleUserSignIn(user, 'secondary');
    } else if (!user && AppState.currentProject === 'secondary') {
        handleUserSignOut();
    }
});

const handleUserSignIn = (user, project) => {
    AppState.currentUser = user;
    AppState.currentProject = project;
    AppState.currentStep = 'userinfo';
    
    elements.userName.textContent = user.displayName || 'User';
    elements.userEmail.textContent = user.email || 'No email';
    elements.userProvider.textContent = `Signed in via ${getProviderName(user)}`;
    elements.userAvatar.src = generateAvatar(user);
    
    elements.userAvatar.onerror = () => {
        elements.userAvatar.src = generateAvatar(user);
    };
    
    console.log(`User signed in to ${project} project:`, user);
    updateUI();
    showLoading(false);
};

const handleUserSignOut = () => {
    AppState.currentUser = null;
    AppState.currentProject = null;
    AppState.currentStep = 'signin';
    
    // Reset form
    elements.emailInput.value = '';
    elements.passwordInput.value = '';
    elements.confirmInput.value = '';
    elements.inputValidation.textContent = '';
    elements.deleteButton.disabled = true;
    
    console.log('User signed out');
    updateUI();
    showLoading(false);
};

// Handle user info section clicks
elements.userInfoSection.addEventListener('click', (e) => {
    if (e.target.closest('.user-card') && AppState.currentStep === 'userinfo') {
        AppState.currentStep = 'delete';
        updateUI();
        clearMessages();
        
        setTimeout(() => {
            elements.confirmInput.focus();
        }, 100);
    }
});

// Initialize the app
document.addEventListener('DOMContentLoaded', () => {
    console.log('Multi-project account deletion portal initialized');
    console.log('Primary project: rewordium');
    console.log('Secondary project: yc-startup-yc');
    
    showLoading(true, 'Initializing...');
    
    // The auth state observers will handle the initial state
    setTimeout(() => {
        if (!AppState.currentUser) {
            showLoading(false);
        }
    }, 3000);
});
