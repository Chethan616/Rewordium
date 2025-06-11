import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:flutter/services.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';

import '../theme/app_theme.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../utils/lottie_assets.dart';
import '../providers/auth_provider.dart';
import '../services/groq_service.dart';
import '../models/persona_model.dart';

// Import your login screen here; adjust path as needed
import 'auth/login_screen.dart';

class ParaphraserPage extends StatefulWidget {
  const ParaphraserPage({super.key});

  @override
  State<ParaphraserPage> createState() => _ParaphraserPageState();
}

class _ParaphraserPageState extends State<ParaphraserPage> {
  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  final TextEditingController _customPromptController = TextEditingController();
  String _selectedMode = "Standard"; // Track the selected mode
  bool _isLoading = false;
  List<String> _alternatives = [];
  Persona? _selectedPersona;
  bool _usePersona = false; // Whether to use persona or mode
  String? _customPrompt; // Store custom prompt for custom mode

  @override
  void initState() {
    super.initState();
    // Initialize Groq service
    GroqService.initialize();
  }

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    _customPromptController.dispose();
    super.dispose();
  }
  
  // Convert mode to tone for API
  String _getToneFromMode(String mode) {
    switch (mode.toLowerCase()) {
      case 'fluency':
        return 'fluent and natural';
      case 'academic':
        return 'academic and scholarly';
      case 'humanize':
        return 'conversational and human-like';
      case 'formal':
        return 'formal and professional';
      case 'simple':
        return 'simple and easy to understand';
      case 'creative':
        return 'creative and imaginative';
      case 'expand':
        return 'detailed and expanded';
      case 'shorten':
        return 'concise and shortened';
      case 'custom':
        return 'unique and distinctive';
      default:
        return 'standard and clear';
    }
  }
  
  // Show persona selection dialog
  void _showPersonaDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Select a Persona'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ...PersonaManager.allPersonas.map((persona) => 
                  ListTile(
                    leading: Text(persona.icon, style: TextStyle(fontSize: 24)),
                    title: Text(persona.name),
                    subtitle: Text(persona.description),
                    onTap: () {
                      setState(() {
                        _selectedPersona = persona;
                      });
                      Navigator.pop(context);
                      
                      // Show a sample text if the text field is empty
                      if (_controller.text.isEmpty) {
                        _controller.text = "The quick brown fox jumps over the lazy dog. This is a sample text that demonstrates how the paraphraser works with different personas.";
                      }
                      
                      // Paraphrase with the selected persona
                      _paraphraseWithPersona();
                    },
                  )
                ).toList(),
                Divider(),
                ListTile(
                  leading: Text('✨', style: TextStyle(fontSize: 24)),
                  title: Text('Create Custom Persona'),
                  subtitle: Text('Define your own paraphrasing instructions'),
                  onTap: () {
                    Navigator.pop(context);
                    _showCustomPersonaDialog();
                  },
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
        ],
      ),
    );
  }
  
  // Show custom persona creation dialog
  void _showCustomPersonaDialog() {
    _customPromptController.text = '';
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Create Custom Persona'),
        content: Container(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Enter instructions for how to paraphrase your text:'),
              SizedBox(height: 8),
              TextField(
                controller: _customPromptController,
                maxLines: 5,
                decoration: InputDecoration(
                  hintText: 'E.g., Rewrite this as if it were written by Shakespeare, using archaic English and poetic structure',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final prompt = _customPromptController.text.trim();
              if (prompt.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter custom instructions')),
                );
                return;
              }
              
              // Create and set custom persona
              PersonaManager.setCustomPersona(prompt);
              setState(() {
                _selectedPersona = PersonaManager.customPersona;
                _usePersona = true;
              });
              
              Navigator.pop(context);
              
              // Show a sample text if the text field is empty
              if (_controller.text.isEmpty) {
                _controller.text = "The quick brown fox jumps over the lazy dog. This is a sample text that demonstrates how the paraphraser works with different personas.";
              }
              
              // Paraphrase with the custom persona
              _paraphraseWithPersona();
            },
            child: Text('Create & Use'),
          ),
        ],
      ),
    );
  }
  
  // Show custom mode dialog
  Future<void> _showCustomModeDialog() async {
    _customPromptController.text = _customPrompt ?? '';
    
    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Custom Paraphrasing Mode'),
        content: Container(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Enter instructions for how to paraphrase your text:'),
              SizedBox(height: 8),
              TextField(
                controller: _customPromptController,
                maxLines: 5,
                decoration: InputDecoration(
                  hintText: 'E.g., Make this text more persuasive and engaging for a marketing audience',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final prompt = _customPromptController.text.trim();
              if (prompt.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter custom instructions')),
                );
                return;
              }
              
              setState(() {
                _customPrompt = prompt;
              });
              
              Navigator.pop(context);
            },
            child: Text('Apply'),
          ),
        ],
      ),
    );
  }
  
  // Paraphrase with selected persona
  Future<void> _paraphraseWithPersona() async {
    if (_selectedPersona == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select a persona first')),
      );
      return;
    }
    
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to paraphrase')),
      );
      return;
    }
    
    setState(() {
      _isLoading = true;
    });
    
    try {
      final result = await GroqService.paraphraseWithPersona(text, _selectedPersona!.prompt);
      
      setState(() {
        _resultController.text = result['paraphrased_text'] ?? text;
        _alternatives = List<String>.from(result['alternatives'] ?? []);
        _isLoading = false;
      });
      
      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error paraphrasing text: $e')),
      );
    }
  }
  
  // Paraphrase the text using Groq with selected mode or persona
  Future<void> _paraphraseText() async {
    // If using persona, call the persona paraphraser instead
    if (_usePersona && _selectedPersona != null) {
      await _paraphraseWithPersona();
      return;
    }
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to paraphrase')),
      );
      return;
    }
    
    setState(() {
      _isLoading = true;
    });
    
    try {
      // Check if custom mode is selected
      if (_selectedMode == "Custom") {
        if (_customPrompt == null || _customPrompt!.isEmpty) {
          // Show dialog to get custom prompt
          await _showCustomModeDialog();
          if (_customPrompt == null || _customPrompt!.isEmpty) {
            setState(() {
              _isLoading = false;
            });
            return;
          }
        }
        
        // Use custom prompt for paraphrasing
        final result = await GroqService.paraphraseWithCustomPrompt(text, _customPrompt!);
        
        setState(() {
          _resultController.text = result['paraphrased_text'] ?? text;
          _alternatives = List<String>.from(result['alternatives'] ?? []);
          _isLoading = false;
        });
      } else {
        // Use standard mode paraphrasing
        final tone = _getToneFromMode(_selectedMode);
        final result = await GroqService.paraphraseText(text, tone);
        
        setState(() {
          _resultController.text = result['paraphrased_text'] ?? text;
          _alternatives = List<String>.from(result['alternatives'] ?? []);
          _isLoading = false;
        });
      }
      
      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error paraphrasing text: $e')),
      );
    }
  }
  
  // Show the paraphrased result
  void _showResultDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Paraphrased Text'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.green.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.green.withOpacity(0.3)),
                  ),
                  child: Text(_resultController.text),
                ),
                if (_alternatives.isNotEmpty) ...[  
                  const SizedBox(height: 16),
                  Text('Alternative Versions:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  ...List.generate(_alternatives.length, (index) {
                    return Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.blue.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.blue.withOpacity(0.3)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Alternative ${index + 1}:',
                              style: TextStyle(fontWeight: FontWeight.bold)),
                          const SizedBox(height: 4),
                          Text(_alternatives[index]),
                          const SizedBox(height: 4),
                          TextButton(
                            onPressed: () {
                              _resultController.text = _alternatives[index];
                              Navigator.pop(context);
                              _showResultDialog(); // Reopen with new selection
                            },
                            child: Text('Use This Version'),
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Close'),
          ),
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: _resultController.text));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Copied to clipboard')),
              );
            },
            child: Text('Copy'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;
    final authProvider = Provider.of<AuthProvider>(context);
    final bool isLoggedIn = authProvider.isLoggedIn;

    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return Column(
              children: [
                CustomAppBar(
                  title: "Paraphraser",
                  leadingIcon: Icon(
                    CupertinoIcons.pencil_outline,
                    color: AppTheme.primaryColor,
                  ),
                  actions: [
                    if (!isLoggedIn)
                      CustomButton(
                        text: "Log in",
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const LoginScreen(),
                            ),
                          );
                        },
                        width: isSmallScreen ? 70 : 90,
                        height: 36,
                        type: ButtonType.primary,
                      ),
                    if (!isLoggedIn) const SizedBox(width: 8),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: Material(
                    elevation: 2,
                    borderRadius: BorderRadius.circular(16),
                    child: SizedBox(
                      height: screenSize.height * 0.17, // Further reduced height
                      child: TextField(
                        controller: _controller,
                        maxLines: null,
                        expands: true,
                        textAlignVertical: TextAlignVertical.top,
                        style: AppTheme.bodyMedium,
                        decoration: InputDecoration(
                          hintText: "Enter text to paraphrase...",
                          contentPadding: const EdgeInsets.symmetric(
                              vertical: 16, horizontal: 16),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(16),
                            borderSide: BorderSide.none,
                          ),
                          filled: true,
                          fillColor: AppTheme.cardColor,
                        ),
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: FadeIn(
                    child: SingleChildScrollView(
                      physics: const BouncingScrollPhysics(),
                      padding: EdgeInsets.zero,
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const SizedBox(height: 8), // Reduced spacing
                          SizedBox(
                            height: isSmallScreen ? 35 : 40, // Further reduced height
                            child: Shimmer.fromColors(
                              baseColor: Colors.green.shade300,
                              highlightColor: Colors.green.shade100,
                              period: const Duration(seconds: 3),
                              child: LottieAssets.getPencilAnimation(),
                            ),
                          ),
                          const SizedBox(height: 12),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            child: Text(
                              "Enter or paste your text and tap\n\"Paraphrase\"",
                              textAlign: TextAlign.center,
                              style: AppTheme.bodyMedium.copyWith(
                                color: AppTheme.textSecondaryColor,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            child: isSmallScreen
                                ? Column(
                                    children: [
                                      CustomButton(
                                        text: "Try Personas",
                                        onPressed: _showPersonaDialog,
                                        icon: CupertinoIcons.person_2_fill,
                                        type: ButtonType.secondary,
                                        width: 160,
                                      ),
                                      const SizedBox(height: 8),
                                      CustomButton(
                                        text: "Paste Text",
                                        onPressed: () async {
                                          try {
                                            final ClipboardData? clipboardData =
                                                await Clipboard.getData(
                                                    Clipboard.kTextPlain);
                                            if (clipboardData != null &&
                                                clipboardData.text != null) {
                                              _controller.text =
                                                  clipboardData.text!;
                                            }
                                          } catch (e) {
                                            ScaffoldMessenger.of(context)
                                                .showSnackBar(
                                              SnackBar(
                                                  content:
                                                      Text('Paste failed: $e')),
                                            );
                                          }
                                        },
                                        icon: Icons.content_paste,
                                        type: ButtonType.secondary,
                                        width: 160,
                                      ),
                                    ],
                                  )
                                : Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      CustomButton(
                                        text: "Try Personas",
                                        onPressed: _showPersonaDialog,
                                        icon: CupertinoIcons.person_2_fill,
                                        type: ButtonType.secondary,
                                        width: 140,
                                      ),
                                      const SizedBox(width: 16),
                                      CustomButton(
                                        text: "Paste Text",
                                        onPressed: () async {
                                          try {
                                            final ClipboardData? clipboardData =
                                                await Clipboard.getData(
                                                    Clipboard.kTextPlain);
                                            if (clipboardData != null &&
                                                clipboardData.text != null) {
                                              _controller.text =
                                                  clipboardData.text!;
                                            }
                                          } catch (e) {
                                            ScaffoldMessenger.of(context)
                                                .showSnackBar(
                                              SnackBar(
                                                  content:
                                                      Text('Paste failed: $e')),
                                            );
                                          }
                                        },
                                        icon: Icons.content_paste,
                                        type: ButtonType.secondary,
                                        width: 140,
                                      ),
                                    ],
                                  ),
                          ),
                          const SizedBox(height: 12),
                        ],
                      ),
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.only(bottom: 0), // Removed bottom padding
                  child: FadeInUp(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _buildParaphraserModes(isSmallScreen),
                        Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 4), // Further reduced padding
                          child: CustomButton(
                            text: "Paraphrase",
                            onPressed: _isLoading ? null : _paraphraseText,
                            width: screenSize.width * 0.8,
                            isLoading: _isLoading,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildParaphraserModes(bool isSmallScreen) {
    // Use smaller mode tabs to prevent overflow
    final List<String> modes = [
      "Standard",
      "Fluency",
      "Academic",
      "Humanize",
      "Formal",
      "Simple",
      "Creative",
      "Expand",
      "Shorten",
      "Custom"
    ];

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
      margin: const EdgeInsets.only(top: 4),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(24),
          topRight: Radius.circular(24),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 8,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Section tabs
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              GestureDetector(
                onTap: () {
                  setState(() {
                    _usePersona = false;
                  });
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                  decoration: BoxDecoration(
                    border: Border(
                      bottom: BorderSide(
                        color: !_usePersona ? AppTheme.primaryColor : Colors.transparent,
                        width: 2,
                      ),
                    ),
                  ),
                  child: Text(
                    "Modes",
                    style: AppTheme.bodyMedium.copyWith(
                      fontWeight: !_usePersona ? FontWeight.bold : FontWeight.normal,
                      color: !_usePersona ? AppTheme.primaryColor : AppTheme.textSecondaryColor,
                    ),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Text("|", style: AppTheme.bodyMedium),
              ),
              GestureDetector(
                onTap: () {
                  setState(() {
                    _usePersona = true;
                  });
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                  decoration: BoxDecoration(
                    border: Border(
                      bottom: BorderSide(
                        color: _usePersona ? AppTheme.primaryColor : Colors.transparent,
                        width: 2,
                      ),
                    ),
                  ),
                  child: Text(
                    "Personas",
                    style: AppTheme.bodyMedium.copyWith(
                      fontWeight: _usePersona ? FontWeight.bold : FontWeight.normal,
                      color: _usePersona ? AppTheme.primaryColor : AppTheme.textSecondaryColor,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          
          // Modes section
          if (!_usePersona) ...[  
            Text("Select Mode", style: AppTheme.bodySmall),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Row(
                  children: modes.map((mode) {
                    return Padding(
                      padding: const EdgeInsets.only(right: 10),
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            _selectedMode = mode;
                          });
                        },
                        child: _buildModeTab(
                          mode,
                          _selectedMode == mode,
                          isSmallScreen,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
          ],
          
          // Personas section
          if (_usePersona) ...[  
            Text("Select Persona", style: AppTheme.bodySmall),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Row(
                  children: PersonaManager.personas.map((persona) {
                    return Padding(
                      padding: const EdgeInsets.only(right: 10),
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            _selectedPersona = persona;
                          });
                        },
                        child: _buildPersonaTab(
                          persona,
                          _selectedPersona?.name == persona.name,
                          isSmallScreen,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildModeTab(String name, bool isSelected, bool isSmallScreen) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          height: isSmallScreen ? 32.0 : 36.0, // Use fixed height to prevent overflow
          padding: EdgeInsets.symmetric(
            horizontal: isSmallScreen ? 10 : 14,
            vertical: 4, // Further reduced vertical padding
          ),
          decoration: BoxDecoration(
            color: isSelected
                ? AppTheme.primaryColor.withOpacity(0.1)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                name,
                style: AppTheme.bodyMedium.copyWith(
                  fontSize: isSmallScreen ? 12 : 14,
                  color: isSelected
                      ? AppTheme.primaryColor
                      : AppTheme.textSecondaryColor,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                ),
              ),
              if (name == "Custom" && isSelected)
                GestureDetector(
                  onTap: () {
                    _showCustomModeDialog();
                  },
                  child: Padding(
                    padding: const EdgeInsets.only(left: 4.0),
                    child: Icon(
                      Icons.edit,
                      size: isSmallScreen ? 14 : 16,
                      color: AppTheme.primaryColor,
                    ),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 4), // Reduced height
        if (isSelected)
          Container(width: 40, height: 2, color: AppTheme.primaryColor), // Reduced height
      ],
    );
  }
  
  Widget _buildPersonaTab(Persona persona, bool isSelected, bool isSmallScreen) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: EdgeInsets.symmetric(
            horizontal: isSmallScreen ? 10 : 14,
            vertical: 6,
          ),
          decoration: BoxDecoration(
            color: isSelected
                ? AppTheme.primaryColor.withOpacity(0.1)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              Text(persona.icon, style: TextStyle(fontSize: isSmallScreen ? 14 : 16)),
              const SizedBox(width: 4),
              Text(
                persona.name,
                style: AppTheme.bodyMedium.copyWith(
                  fontSize: isSmallScreen ? 12 : 14,
                  color: isSelected
                      ? AppTheme.primaryColor
                      : AppTheme.textSecondaryColor,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        if (isSelected)
          Container(width: 40, height: 3, color: AppTheme.primaryColor),
      ],
    );
  }
}
