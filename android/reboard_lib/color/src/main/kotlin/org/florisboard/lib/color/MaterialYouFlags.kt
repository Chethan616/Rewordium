/*
 * Copyright (C) 2025 The ReBoard Contributors
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

package org.florisboard.lib.color

import androidx.compose.runtime.saveable.Saver
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Custom serializer for PaletteStyle
object PaletteStyleSerializer : KSerializer<PaletteStyle> {
    override val descriptor = PrimitiveSerialDescriptor("PaletteStyle", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: PaletteStyle) = encoder.encodeInt(value.ordinal)
    override fun deserialize(decoder: Decoder): PaletteStyle =
        PaletteStyle.entries.getOrElse(decoder.decodeInt()) { PaletteStyle.Neutral }
}

// Custom serializer for Contrast
object ContrastSerializer : KSerializer<Contrast> {
    override val descriptor = PrimitiveSerialDescriptor("Contrast", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Contrast) = encoder.encodeInt(value.ordinal)
    override fun deserialize(decoder: Decoder): Contrast =
        Contrast.entries.getOrElse(decoder.decodeInt()) { Contrast.Default }
}

// Custom serializer for ColorSpec.SpecVersion
object SpecVersionSerializer : KSerializer<ColorSpec.SpecVersion> {
    override val descriptor = PrimitiveSerialDescriptor("SpecVersion", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: ColorSpec.SpecVersion) = encoder.encodeInt(value.ordinal)
    override fun deserialize(decoder: Decoder): ColorSpec.SpecVersion =
        ColorSpec.SpecVersion.entries.getOrElse(decoder.decodeInt()) { ColorSpec.SpecVersion.Default }
}

@Serializable
data class MaterialYouFlags(
    @Serializable(with = PaletteStyleSerializer::class)
    val paletteStyle: PaletteStyle = PaletteStyle.Neutral,
    @Serializable(with = ContrastSerializer::class)
    val contrastLevel: Contrast = Contrast.Default,
    @Serializable(with = SpecVersionSerializer::class)
    val specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.Default,
    val usePitchBlack: Boolean = false,
)

val MaterialYouFlagsSaver = Saver<MaterialYouFlags, String>(
    save = { flags ->
        // Simple ordinal-based serialization
        "${flags.paletteStyle.ordinal},${flags.contrastLevel.ordinal},${flags.specVersion.ordinal},${flags.usePitchBlack}"
    },
    restore = { str ->
        val parts = str.split(",")
        if (parts.size == 3 || parts.size == 4) {
            MaterialYouFlags(
                paletteStyle = PaletteStyle.entries.getOrElse(parts[0].toIntOrNull() ?: 0) { PaletteStyle.Neutral },
                contrastLevel = Contrast.entries.getOrElse(parts[1].toIntOrNull() ?: 0) { Contrast.Default },
                specVersion = ColorSpec.SpecVersion.entries.getOrElse(parts[2].toIntOrNull() ?: 0) { ColorSpec.SpecVersion.Default },
                usePitchBlack = if (parts.size >= 4) parts[3].toBooleanStrictOrNull() ?: false else false,
            )
        } else {
            MaterialYouFlags()
        }
    }
)
