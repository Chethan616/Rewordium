import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import '../providers/keyboard_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/custom_app_bar.dart';
import 'package:lottie/lottie.dart';

class PersonaManagementScreen extends StatefulWidget {
  const PersonaManagementScreen({Key? key}) : super(key: key);

  @override
  State<PersonaManagementScreen> createState() => _PersonaManagementScreenState();
}

class _PersonaManagementScreenState extends State<PersonaManagementScreen> with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _descriptionController = TextEditingController();
  
  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );
    _animationController.forward();
  }
  
  @override
  void dispose() {
    _animationController.dispose();
    _nameController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  void _showAddPersonaDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create New Persona'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Persona Name',
                hintText: 'e.g., Professional',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _descriptionController,
              decoration: const InputDecoration(
                labelText: 'Description',
                hintText: 'e.g., Formal and business-like tone',
              ),
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (_nameController.text.isNotEmpty && _descriptionController.text.isNotEmpty) {
                final provider = Provider.of<KeyboardProvider>(context, listen: false);
                provider.addPersona(_nameController.text, _descriptionController.text);
                _nameController.clear();
                _descriptionController.clear();
                Navigator.pop(context);
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  Widget _buildPersonaCard(Persona persona, bool isSelected) {
    return GestureDetector(
      onTap: () {
        final provider = Provider.of<KeyboardProvider>(context, listen: false);
        provider.setActivePersona(persona.name);
      },
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
        decoration: BoxDecoration(
          color: isSelected ? AppTheme.primaryColor.withOpacity(0.1) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
          border: isSelected 
              ? Border.all(color: AppTheme.primaryColor, width: 2)
              : Border.all(color: Colors.grey.shade200),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Row(
            children: [
              Container(
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: isSelected ? AppTheme.primaryColor : Colors.grey.shade200,
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Icon(
                    _getPersonaIcon(persona.name),
                    color: isSelected ? Colors.white : Colors.grey.shade600,
                    size: 24,
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      persona.name,
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: isSelected ? AppTheme.primaryColor : Colors.black,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      persona.description,
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.grey.shade600,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              if (isSelected)
                const Icon(
                  Icons.check_circle,
                  color: AppTheme.primaryColor,
                  size: 24,
                ),
            ],
          ),
        ),
      ),
    );
  }

  IconData _getPersonaIcon(String personaName) {
    switch (personaName.toLowerCase()) {
      case 'happy':
        return Icons.sentiment_satisfied_alt;
      case 'sad':
        return Icons.sentiment_dissatisfied;
      case 'humor':
        return Icons.sentiment_very_satisfied;
      case 'formal':
        return Icons.business;
      case 'casual':
        return Icons.chat_bubble_outline;
      default:
        return Icons.person_outline;
    }
  }

  @override
  Widget build(BuildContext context) {
    final keyboardProvider = Provider.of<KeyboardProvider>(context);
    final personas = keyboardProvider.personas;
    final activePersona = keyboardProvider.activePersona;

    return Scaffold(
      appBar: CustomAppBar(
        title: 'Keyboard Personas',
        showBackButton: true,
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              'Select a persona to change how your text is paraphrased',
              style: TextStyle(
                fontSize: 16,
                color: Colors.grey.shade600,
              ),
              textAlign: TextAlign.center,
            ),
          ),
          SizedBox(
            height: 120,
            child: Lottie.asset(
              'assets/lottie/personas.json',
              controller: _animationController,
              fit: BoxFit.contain,
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: personas.length,
              padding: const EdgeInsets.only(bottom: 80),
              itemBuilder: (context, index) {
                final persona = personas[index];
                final isSelected = persona.name == activePersona;
                return _buildPersonaCard(persona, isSelected);
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddPersonaDialog,
        backgroundColor: AppTheme.primaryColor,
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }
}
