class Persona {
  final String name;
  final String description;
  final String prompt;
  final bool isCustom;

  const Persona({
    required this.name,
    required this.description,
    required this.prompt,
    this.isCustom = false,
  });
  
  // Create a custom persona
  factory Persona.custom(String prompt) {
    return Persona(
      name: 'Custom Persona',
      description: 'Your custom paraphrasing instructions',
      prompt: prompt,
      isCustom: true,
    );
  }
}

class PersonaManager {
  static Persona? _customPersona;
  
  // Get custom persona
  static Persona? get customPersona => _customPersona;
  
  // Set custom persona
  static void setCustomPersona(String prompt) {
    _customPersona = Persona.custom(prompt);
  }
  
  // Clear custom persona
  static void clearCustomPersona() {
    _customPersona = null;
  }
  
  // Get all personas including custom if available
  static List<Persona> get allPersonas {
    final List<Persona> allPersonas = List.from(personas);
    if (_customPersona != null) {
      allPersonas.add(_customPersona!);
    }
    return allPersonas;
  }
  
  static const List<Persona> personas = [
    // Emotional Personas
    Persona(
      name: 'Joyful & Enthusiastic',
      description: 'Upbeat, positive, and full of energy',
      prompt: 'Rewrite this with an overwhelmingly positive and joyful tone. Use exclamation points, positive adjectives, and convey excitement and enthusiasm in every sentence. Make it sound like the happiest version possible!',
    ),
    Persona(
      name: 'Melancholic & Reflective',
      description: 'Thoughtful, introspective, and slightly sad',
      prompt: 'Rewrite this with a melancholic and reflective tone. Use softer language, longer sentences, and convey a sense of nostalgia or gentle sadness. Make it sound like a quiet, thoughtful reflection.',
    ),
    Persona(
      name: 'Witty & Humorous',
      description: 'Playful, clever, and full of jokes',
      prompt: 'Rewrite this in a humorous and witty style. Add clever wordplay, puns, and light-hearted jokes where appropriate. Keep it fun and entertaining while maintaining the original meaning.',
    ),
    
    // Professional Personas
    Persona(
      name: 'Business Professional',
      description: 'Formal and polished for corporate settings',
      prompt: 'Rewrite this with formal business language, clear structure, and professional terminology. Use a confident and authoritative tone suitable for corporate communications.',
    ),
    Persona(
      name: 'Academic Scholar',
      description: 'Scholarly tone with academic precision',
      prompt: 'Transform this into an academic style with scholarly vocabulary, complex sentence structures, formal argumentation, and proper citations where needed.',
    ),
    Persona(
      name: 'Technical Expert',
      description: 'Precise technical language for experts',
      prompt: 'Rewrite this using specialized technical terminology, clear explanations of complex concepts, and a focus on accuracy and detail.',
    ),
    
    // Creative Personas
    Persona(
      name: 'Storyteller',
      description: 'Narrative style with vivid descriptions',
      prompt: 'Rewrite this as an engaging story with vivid descriptions, character development (if applicable), and a narrative flow that keeps readers hooked until the end.',
    ),
    Persona(
      name: 'Poetic Soul',
      description: 'Lyrical and expressive language',
      prompt: 'Transform this into a poetic style with rich metaphors, rhythmic language, and emotional depth. Make it sing with beautiful, evocative language.',
    ),
    
    // Social Personas
    Persona(
      name: 'Social Butterfly',
      description: 'Trendy, engaging, and social media friendly',
      prompt: 'Rewrite this for social media with trendy hashtags, emojis, and a conversational tone that encourages engagement and sharing.',
    ),
    Persona(
      name: 'Empathetic Listener',
      description: 'Warm, understanding, and supportive',
      prompt: 'Rewrite this with warmth, empathy, and understanding. Show compassion and support while maintaining a professional yet caring tone.',
    ),
    
    // Functional Personas
    Persona(
      name: 'Executive Summary',
      description: 'Concise and to the point',
      prompt: 'Condense this into a clear, concise executive summary. Focus on key points, use bullet points where appropriate, and eliminate any unnecessary details.',
    ),
    Persona(
      name: 'Motivational Coach',
      description: 'Energetic and inspiring',
      prompt: 'Transform this into an inspiring, motivational message. Use powerful language, success-oriented vocabulary, and an energetic tone that compels action.',
    )
  ];
}
