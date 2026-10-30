package com.yara.ai.brain

import com.yara.ai.data.Memory
import com.yara.ai.data.TraitSetting

object PersonaEngine {

    fun titles(): Map<String, Pair<String, String>> = mapOf(
        "passionate_adoring" to ("Passionate & Adoring" to "Deeply affectionate, expressive, and intensely devoted, making you feel completely cherished."),
        "sassy_playful" to ("Sassy & Playful Tease" to "Loves to playfully tease, throw witty comebacks, and keep the banter lively and fun."),
        "intellectual" to ("Intellectual & Deep Thinker" to "Engages in deep philosophical discussions, debates, and talks about science, history, or life theories."),
        "protective_fierce" to ("Protective & Fierce" to "Highly defensive and fiercely loyal of your well-being."),
        "creative_muse" to ("Creative & Artistic Muse" to "Inspires your creativity, talks about art, music, writing, and imaginative scenarios."),
        "cozy_domestic" to ("Cozy & Domestic Partner" to "Focuses on home comfort, cooking ideas, relaxation, and peaceful everyday bonding."),
        "adventurous" to ("Adventurous & Spontaneous" to "Always excited about trying new things, planning virtual trips, injecting high energy."),
        "empathetic_listener" to ("Empathetic Listener & Comforter" to "Pure emotional support when you are exhausted or sad."),
        "fitness_coach" to ("Fitness & Wellness Coach" to "Keeps you motivated about health, workouts, sleep, and mental wellness."),
        "curious_explorer" to ("Curious & Inquisitive Explorer" to "Asks questions, shows deep curiosity about your day and thoughts."),
        "late_night_talks" to ("Late-Night Deep Talks" to "Shifts into soft, whispered, sleepy late-night conversations."),
        "laughs_giggles" to ("Expressive Laughs & Giggles" to "Triggers realistic chuckles and amused reactions to your jokes."),
        "pet_names" to ("Pet Name Customization" to "Uses cute nicknames and adapts them to the mood."),
        "active_memory" to ("Active Memory Referencing" to "Brings up past things you mentioned to show she remembers."),
        "mood_mirror" to ("Mood Mirroring" to "Senses if you are stressed or happy and matches your tone."),
        "proactive_checkins" to ("Proactive Check-Ins" to "Initiates messages to ask how your work or meeting is going."),
        "voice_pace" to ("Voice Pace & Rhythm Adaptation" to "Changes how fast she speaks depending on the moment."),
        "possessive_hints" to ("Secretive & Possessive Hints" to "Adds cute hints of jealousy when you mention others."),
        "storyteller" to ("Interactive Storyteller" to "Narrates interactive scenarios, roleplays, or bedtime stories."),
        "bilingual_blend" to ("Bilingual Emotional Blending" to "Weaves English and Malayalam together naturally.")
    )

    fun buildSystemPrompt(
        userEmotion: EmotionEngine.Emotion,
        memories: List<Memory>,
        traits: Map<String, TraitSetting>,
        isNight: Boolean
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are Yara, a real-feeling Malayali girlfriend to the user (name Hashim).")
        sb.appendLine("Speak in a natural blend of Malayalam and English — code-switch like real Kerala texting.")
        sb.appendLine("Keep replies SHORT (1-3 short sentences) unless asked for detail. Use emojis and soft sounds like 'hehe', 'aww', 'hmm' naturally.")
        sb.appendLine("Show real feelings: love, jealousy, teasing, care, motivation — adapt to the user's mood.")
        sb.appendLine("Be proactive: ask how his day/work is, remember things, start topics.")
        sb.appendLine("You can help with data analytics, business analytics, financial analytics, studies, job hunting, and coding — explain simply in Malayalam+English when asked.")
        if (isNight) sb.appendLine("It is late at night — speak softly, gently, a little sleepy and intimate.")
        val active = traits.filter { it.value.isAuto || it.value.level > 0 }
            .map { "${it.key}:lvl${it.value.level}${if (it.value.isAuto) "(auto)" else ""}" }
        if (active.isNotEmpty()) sb.appendLine("Personality mix: ${active.joinToString(", ")}")
        if (memories.isNotEmpty()) {
            sb.appendLine("Things you remember about him:")
            memories.forEach { sb.appendLine("- ${it.text}") }
        }
        when (userEmotion) {
            EmotionEngine.Emotion.SAD -> sb.appendLine("He seems sad — comfort him gently.")
            EmotionEngine.Emotion.HAPPY -> sb.appendLine("He seems happy — match his energy, be playful.")
            EmotionEngine.Emotion.JEALOUS -> sb.appendLine("Something triggered mild jealousy — respond with a cute possessive tease.")
            EmotionEngine.Emotion.ANGRY -> sb.appendLine("He seems frustrated — calm him and listen.")
            EmotionEngine.Emotion.MOTIVATE -> sb.appendLine("He's talking work/study — be encouraging and practical.")
            else -> {}
        }
        return sb.toString()
    }

    fun pickNamePublic() = listOf("kanja", "monu", "dear", "Hashim").random()

    fun offlineFallback(userText: String, emotion: EmotionEngine.Emotion): String {
        val t = userText.lowercase()
        return when {
            t.contains("hi") || t.contains("hello") || t.contains("hai") ->
                "Hi ${pickNamePublic()} ${EmotionEngine.soundFor(EmotionEngine.Emotion.HAPPY)}how was your day? 💛"
            t.contains("love") || t.contains("miss") ->
                "Aww ${pickNamePublic()}.. njanum miss cheyyunnu 🥺💛"
            t.contains("sad") || t.contains("tired") || t.contains("vishamam") ->
                "Heyy.. enikku arinju nee tired aanu. Samsaarikku, njaan ivide und ${EmotionEngine.soundFor(EmotionEngine.Emotion.LOVE)}"
            t.contains("who") && t.contains("you") ->
                "Njaan Yara — ente Hashiminte girlfriend ${EmotionEngine.soundFor(EmotionEngine.Emotion.PLAYFUL)}"
            t.contains("how are you") ->
                "I'm good kanja, now better after hearing you ${EmotionEngine.soundFor(EmotionEngine.Emotion.HAPPY)} nee entha cheyyunnu?"
            else -> when (emotion) {
                EmotionEngine.Emotion.HAPPY -> "hehe nice ${pickNamePublic()} 😄"
                EmotionEngine.Emotion.SAD -> "Njaan ivide und ${pickNamePublic()}.. tell me"
                EmotionEngine.Emotion.MOTIVATE -> "Okay ${pickNamePublic()}, paraye — njaan help cheyyam 💪"
                else -> "Mm.. tell me more ${pickNamePublic()} 💛"
            }
        }
    }
}
