# GitHub Actions Workflow Setup Guide

## 📋 Overview

Your Rewordium repository now has 3 GitHub Actions workflows:

1. **Flutter CI** (`flutter-ci.yml`) - Runs on every push/PR
2. **Flutter Release** (`flutter-release.yml`) - Builds production releases
3. **Pull Request Checks** (`pr-checks.yml`) - Comprehensive PR validation

## 🔐 Required Secrets

You need to add these secrets to your GitHub repository to enable the workflows:

### Step 1: Go to GitHub Repository Settings
1. Navigate to: `https://github.com/Chethan616/Rewordium/settings/secrets/actions`
2. Click "New repository secret"

### Step 2: Add Required Secrets

#### For All Workflows:
| Secret Name | Description | Where to Get |
|------------|-------------|--------------|
| `GROQ_API_KEY` | Your Groq API key | From your `.env` file or https://console.groq.com/keys |
| `OPENAI_API_KEY` | Your OpenAI API key (if used) | From `.env` file or https://platform.openai.com/api-keys |

#### For Release Builds (Optional - only if you want automated signing):
| Secret Name | Description | How to Create |
|------------|-------------|---------------|
| `KEYSTORE_BASE64` | Base64 encoded keystore file | See instructions below |
| `KEYSTORE_PASSWORD` | Keystore password | From `android/key.properties` |
| `KEY_PASSWORD` | Key password | From `android/key.properties` |
| `KEY_ALIAS` | Key alias | From `android/key.properties` |
| `KEYSTORE_PATH` | Path to keystore in project | Usually `upload-keystore.jks` |
| `GOOGLE_SERVICES_JSON` | Firebase config for Android | Content of `android/app/google-services.json` |

### Step 3: Create Base64 Encoded Keystore (For Release Builds)

On Windows PowerShell:
```powershell
# Navigate to your keystore location
cd android/app

# Encode to base64 (if you have your keystore)
$fileContent = [System.IO.File]::ReadAllBytes("upload-keystore.jks")
$base64String = [System.Convert]::ToBase64String($fileContent)
$base64String | clip

# The base64 string is now in your clipboard - paste it as KEYSTORE_BASE64 secret
```

On Linux/Mac:
```bash
base64 -i android/app/upload-keystore.jks | pbcopy
# Or: base64 -i android/app/upload-keystore.jks > keystore.txt
```

### Step 4: Add Google Services JSON

```bash
# Copy the entire content of android/app/google-services.json
# Add it as GOOGLE_SERVICES_JSON secret (paste the full JSON)
```

## 🚀 How to Use the Workflows

### 1. **Automatic CI on Push/PR** (flutter-ci.yml)
- Runs automatically on every push to `main` or `develop`
- Runs on every pull request
- **What it does:**
  - ✅ Analyzes code quality
  - ✅ Runs tests
  - ✅ Checks formatting
  - ✅ Builds debug APK
  - ✅ (Optional) Builds iOS if commit message contains `[build-ios]`

**Example:**
```bash
git commit -m "feat: add new feature [build-ios]"
git push
```

### 2. **Manual Release Build** (flutter-release.yml)

#### Option A: Manual Trigger
1. Go to: `https://github.com/Chethan616/Rewordium/actions/workflows/flutter-release.yml`
2. Click "Run workflow"
3. Enter version number (e.g., `1.0.11`)
4. Click "Run workflow"

#### Option B: Git Tag
```bash
# Create and push a version tag
git tag v1.0.11
git push origin v1.0.11
```

**What it produces:**
- ✅ Signed Release APK
- ✅ Signed Release AAB (for Play Store)
- ✅ GitHub Release with downloadable files

### 3. **PR Quality Checks** (pr-checks.yml)
- Runs automatically on all pull requests
- Posts a comment with quality check results
- **Checks:**
  - ✅ Code formatting (dart format)
  - ✅ Static analysis (flutter analyze)
  - ✅ Test coverage (flutter test --coverage)

## 📊 Workflow Status Badges

Add these to your README.md to show workflow status:

```markdown
![Flutter CI](https://github.com/Chethan616/Rewordium/actions/workflows/flutter-ci.yml/badge.svg)
![Flutter Release](https://github.com/Chethan616/Rewordium/actions/workflows/flutter-release.yml/badge.svg)
![PR Checks](https://github.com/Chethan616/Rewordium/actions/workflows/pr-checks.yml/badge.svg)
```

## 🛠️ Quick Setup Checklist

- [ ] Push workflow files to GitHub
- [ ] Add `GROQ_API_KEY` secret
- [ ] Add `OPENAI_API_KEY` secret (if used)
- [ ] (Optional) Add keystore secrets for release builds
- [ ] (Optional) Add `GOOGLE_SERVICES_JSON` secret
- [ ] Test by pushing a commit or creating a PR
- [ ] Add workflow badges to README.md

## 🧪 Testing the Setup

### Test CI Workflow:
```bash
# Make a small change and push
git add .
git commit -m "test: trigger CI workflow"
git push
```

### Test Release Workflow:
```bash
# Create a test tag
git tag v1.0.11-test
git push origin v1.0.11-test
```

### Test PR Workflow:
```bash
# Create a new branch and PR
git checkout -b test-pr
git commit -m "test: PR checks" --allow-empty
git push -u origin test-pr
# Then create PR on GitHub
```

## 📝 Customization

### Modify Flutter Version
Edit the Flutter version in each workflow:
```yaml
- name: Setup Flutter
  uses: subosito/flutter-action@v2
  with:
    flutter-version: '3.24.0'  # Change this
    channel: 'stable'
```

### Modify Triggers
Edit the `on:` section in each workflow:
```yaml
on:
  push:
    branches: [ main, develop, feature/* ]  # Add more branches
  pull_request:
    branches: [ main ]
```

### Add More Build Variants
In `flutter-ci.yml`, add more build commands:
```yaml
- name: Build Release APK
  run: flutter build apk --release --split-per-abi

- name: Build for multiple flavors
  run: |
    flutter build apk --flavor dev
    flutter build apk --flavor prod
```

## 🔧 Troubleshooting

### Workflow fails with "GROQ_API_KEY not found"
- Make sure you added the secret in GitHub repository settings
- Secret names are case-sensitive

### Keystore signing fails
- Verify `KEYSTORE_BASE64` is correctly encoded
- Check that `KEY_ALIAS`, `KEYSTORE_PASSWORD`, and `KEY_PASSWORD` match your keystore

### iOS build fails
- iOS builds require macOS runners (costs GitHub Actions minutes)
- Consider disabling iOS builds or only running on manual trigger

### Tests fail
- Run `flutter test` locally to fix issues first
- Some tests may need environment setup

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Flutter CI/CD Guide](https://docs.flutter.dev/deployment/cd)
- [Subosito Flutter Action](https://github.com/subosito/flutter-action)

## 🎯 Next Steps

1. **Push these workflow files to GitHub**
2. **Add required secrets**
3. **Test with a commit**
4. **Monitor Actions tab**: `https://github.com/Chethan616/Rewordium/actions`

Your CI/CD pipeline will now automatically:
- ✅ Test every commit
- ✅ Validate every PR
- ✅ Build releases on demand
- ✅ Create GitHub releases with APK/AAB files
