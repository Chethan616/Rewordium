# Third-Party Licenses

This module depends on the following third-party components. Each is
redistributed under its original license, with all upstream copyright and
attribution notices preserved.

## AOSP LatinIME

- **Location**: `src/main/cpp/aosp/` (git submodule, untouched upstream)
- **Upstream**: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- **Pinned commit**: `android-16.0.0_r4`
- **License**: Apache License, Version 2.0
- **License text**: https://www.apache.org/licenses/LICENSE-2.0
- **Copyright**: Copyright (C) The Android Open Source Project

The submodule directory contains a copy of the Apache 2.0 LICENSE and NOTICE
files at its root (`src/main/cpp/aosp/NOTICE`). Each individual source file
retains its per-file Apache 2.0 header verbatim.

Per Apache 2.0 Section 4, redistribution requires (a) a copy of the License,
(b) prominent notice of any modifications, and (c) retention of all copyright,
patent, trademark, and attribution notices from the source. We satisfy all
three by (a) shipping the upstream NOTICE and LICENSE inside the submodule,
(b) making no modifications to the submodule contents, and (c) leaving every
file header untouched.

Our own integration code (CMake build, JNI shim, Kotlin wrappers) is original
work and is licensed under the same Apache 2.0 terms as the rest of this
project.

## Material 3 Expressive patterns

- **Upstream**: https://github.com/meticha/material-3-expressive-catalog
- **License**: Apache License 2.0

## Bot avatars

- **Location**: `app/src/main/kotlin/com/noxquill/rewordium/app/ui/avatar/`
- **Upstream**: Libraries.dev bot-avatars, by Jakub Antalik
- **License**: MIT License
- **Copyright**: Copyright (c) 2026 Jakub Antalik

MIT License

Copyright (c) 2026 Jakub Antalik

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
