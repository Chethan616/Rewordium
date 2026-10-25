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
