/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noxquill.rewordium.keyboard.app.settings.stickerstudio

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around Google ML Kit Subject Segmentation. On-device,
 * Play-Services-backed; the first call downloads the model (≈25 MB) which
 * is cached for subsequent runs.
 *
 * `getForegroundBitmap()` returns the input bitmap with non-subject pixels
 * already alpha-cleared — what we want for one-tap sticker cutout. No
 * mask compositing needed on our side.
 *
 * Returns null if the model isn't available (no Play Services / device
 * doesn't support it) or no subject was detected. The caller surfaces a
 * "couldn't find a subject — try a clearer photo" hint in that case.
 */
object SubjectSegmentationHelper {

    suspend fun run(source: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        val image = InputImage.fromBitmap(source, 0)
        segmenter.process(image)
            .addOnSuccessListener { result ->
                cont.resume(result.foregroundBitmap)
                runCatching { segmenter.close() }
            }
            .addOnFailureListener {
                cont.resume(null)
                runCatching { segmenter.close() }
            }
        cont.invokeOnCancellation { runCatching { segmenter.close() } }
    }
}
