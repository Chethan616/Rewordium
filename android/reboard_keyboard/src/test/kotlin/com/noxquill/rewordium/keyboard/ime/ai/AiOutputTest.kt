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

package com.noxquill.rewordium.keyboard.ime.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiOutputTest {
    private val chat = "hey i cant make it tmrw, sorry man"

    @Test
    fun `plain answer passes through`() {
        assertEquals("Hey, I can't make it tomorrow. Sorry, man.", AiOutput.clean("Hey, I can't make it tomorrow. Sorry, man.", chat))
    }

    @Test
    fun `think blocks, fences and preambles are removed`() {
        assertEquals("Fixed text.", AiOutput.clean("<think>plan</think>\nFixed text.", chat))
        assertEquals("Fixed text.", AiOutput.clean("```\nFixed text.\n```", chat))
        assertEquals("Fixed text.", AiOutput.clean("Here is the rewritten text:\n\nFixed text.", chat))
        assertEquals("Fixed text.", AiOutput.clean("Sure! Here's a more formal version:\nFixed text.", chat))
    }

    @Test
    fun `the user's own first line is not mistaken for a preamble`() {
        val source = "here is the plan:\nwe meet at 5"
        assertEquals("Here is the plan:\nWe meet at 5.", AiOutput.clean("Here is the plan:\nWe meet at 5.", source))
    }

    @Test
    fun `wrapping quotes are dropped unless the source had them`() {
        assertEquals("See you soon", AiOutput.clean("\"See you soon\"", "see u soon"))
        assertEquals("\"See you soon\"", AiOutput.clean("\"See you soon\"", "\"see u soon\""))
    }

    @Test
    fun `curly quotes follow the writer's style`() {
        assertEquals("I can't, it's late", AiOutput.clean("I can’t, it’s late", "i cant its late"))
        assertEquals("I can’t", AiOutput.clean("I can’t", "i can’t"))
    }

    @Test
    fun `odd spaces and trailing whitespace are normalized`() {
        assertEquals("Meeting at 5 baje\n• one\n• two", AiOutput.clean("Meeting at 5 baje  \n• one  \n• two", "meeting 5 baje"))
    }

    @Test
    fun `empty or identity answers are rejected`() {
        assertNull(AiOutput.clean("   ", chat))
        assertNull(AiOutput.clean("As an AI, I cannot do that.", chat))
        assertNull(AiOutput.clean("<think>only thinking", chat))
    }

    @Test
    fun `variants are parsed from a JSON array`() {
        val raw = "[\"Can't make it tomorrow, sorry!\", \"I won’t be able to come tomorrow.\", \"Tomorrow's off for me, sorry.\"]"
        val variants = AiOutput.cleanVariants(raw, chat)
        assertEquals(3, variants.size)
        assertEquals("I won't be able to come tomorrow.", variants[1])
    }

    @Test
    fun `variants fall back to the whole answer`() {
        assertEquals(listOf("Just one answer."), AiOutput.cleanVariants("Just one answer.", chat))
        assertEquals(listOf("Inside text"), AiOutput.cleanVariants("Options:\n[\"Inside text\"]", chat))
    }

    @Test
    fun `prompts carry the tool task and output contract`() {
        val single = AiPrompts.build(AiTool.PROFESSIONAL, chat, AiOptions())
        assertTrue("professional tone" in single.system)
        assertTrue("Output only the finished text" in single.system)
        assertTrue(single.user.contains(chat))

        val variants = AiPrompts.build(AiTool.REWRITE, chat, AiOptions(), variants = 3)
        assertTrue("exactly 3 different versions as a JSON array" in variants.system)

        val translate = AiPrompts.build(AiTool.TRANSLATE, chat, AiOptions(targetLanguage = "Hinglish"))
        assertTrue("Translate the text into Hinglish" in translate.system)
        assertTrue("Never translate" !in translate.system)

        val reply = AiPrompts.build(AiTool.REPLY, "", AiOptions(conversation = "Priya: coming saturday?"), variants = 3)
        assertTrue("<conversation>" in reply.user && "<what_i_want_to_say>" !in reply.user)
    }
}
