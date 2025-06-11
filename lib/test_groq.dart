import 'package:flutter/material.dart';
import 'services/groq_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Groq service
  await GroqService.initialize();
  
  runApp(const TestGroqApp());
}

class TestGroqApp extends StatelessWidget {
  const TestGroqApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Groq Test',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: const TestGroqScreen(),
    );
  }
}

class TestGroqScreen extends StatefulWidget {
  const TestGroqScreen({Key? key}) : super(key: key);

  @override
  State<TestGroqScreen> createState() => _TestGroqScreenState();
}

class _TestGroqScreenState extends State<TestGroqScreen> {
  final TextEditingController _textController = TextEditingController();
  String _paraphrasedText = '';
  bool _isLoading = false;
  String _errorMessage = '';

  Future<void> _paraphraseText() async {
    final text = _textController.text;
    if (text.isEmpty) {
      setState(() {
        _errorMessage = 'Please enter some text to paraphrase';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = '';
    });

    try {
      final result = await GroqService.paraphraseText(text, 'natural');
      
      setState(() {
        _isLoading = false;
        if (result.containsKey('error')) {
          _errorMessage = result['error'];
        } else {
          _paraphrasedText = result['paraphrased_text'] ?? 'No paraphrased text returned';
        }
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = 'Error: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Groq Paraphrasing Test'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _textController,
              maxLines: 5,
              decoration: const InputDecoration(
                hintText: 'Enter text to paraphrase',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _isLoading ? null : _paraphraseText,
              child: _isLoading 
                ? const CircularProgressIndicator(color: Colors.white)
                : const Text('Paraphrase with Groq'),
            ),
            const SizedBox(height: 16),
            if (_errorMessage.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(8),
                color: Colors.red.shade100,
                child: Text(
                  _errorMessage,
                  style: const TextStyle(color: Colors.red),
                ),
              ),
            if (_paraphrasedText.isNotEmpty) ...[
              const SizedBox(height: 16),
              const Text(
                'Paraphrased Text:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(_paraphrasedText),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
