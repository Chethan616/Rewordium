import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:m3e_collection/m3e_collection.dart';
import '../models/document_result.dart';
import '../services/document_service.dart';

/// URL import bottom sheet dialog.
/// Allows pasting a URL and importing text content from it.
class UrlImportDialog extends StatefulWidget {
  final void Function(DocumentResult result) onImported;

  const UrlImportDialog({super.key, required this.onImported});

  @override
  State<UrlImportDialog> createState() => _UrlImportDialogState();
}

class _UrlImportDialogState extends State<UrlImportDialog> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  bool _isLoading = false;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    // Auto-focus after sheet animation
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  bool get _isValidUrl {
    final text = _controller.text.trim();
    if (text.isEmpty) return false;
    try {
      final uri = Uri.parse(text);
      return uri.hasScheme && (uri.scheme == 'http' || uri.scheme == 'https');
    } catch (_) {
      return false;
    }
  }

  Future<void> _importUrl() async {
    final url = _controller.text.trim();
    if (!_isValidUrl) {
      setState(() => _errorText = 'Please enter a valid URL (https://...)');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    try {
      final result = await DocumentService.importFromUrl(url);
      if (result != null && result.text.isNotEmpty) {
        if (mounted) Navigator.of(context).pop();
        widget.onImported(result);
      } else {
        setState(() {
          _errorText = 'Could not extract text from this URL';
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _errorText = 'Failed to import: ${e.toString().replaceAll('Exception: ', '')}';
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return FadeInUp(
      duration: const Duration(milliseconds: 300),
      child: Container(
        margin: const EdgeInsets.all(16),
        padding: EdgeInsets.only(bottom: bottomInset > 0 ? bottomInset : 16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Handle bar
            Center(
              child: Container(
                margin: const EdgeInsets.only(top: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0891B2).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      CupertinoIcons.link,
                      color: Color(0xFF0891B2),
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Import from URL',
                          style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                            fontWeight: FontWeight.w700,
                            fontSize: 16,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Paste a web page or PDF link',
                          style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            // URL text field
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                controller: _controller,
                focusNode: _focusNode,
                keyboardType: TextInputType.url,
                autocorrect: false,
                textInputAction: TextInputAction.go,
                onSubmitted: (_) => _importUrl(),
                onChanged: (_) {
                  setState(() {
                    _errorText = null;
                  });
                },
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(fontSize: 14),
                decoration: InputDecoration(
                  hintText: 'https://example.com/article',
                  hintStyle: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
                    fontSize: 14,
                  ),
                  prefixIcon: Padding(
                    padding: const EdgeInsets.only(left: 14, right: 8),
                    child: Icon(
                      CupertinoIcons.globe,
                      color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
                      size: 18,
                    ),
                  ),
                  prefixIconConstraints: const BoxConstraints(
                    minWidth: 40,
                    minHeight: 0,
                  ),
                  suffixIcon: _controller.text.isNotEmpty
                      ? GestureDetector(
                          onTap: () {
                            _controller.clear();
                            setState(() => _errorText = null);
                          },
                          child: Icon(
                            CupertinoIcons.xmark_circle_fill,
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                            size: 18,
                          ),
                        )
                      : null,
                  filled: true,
                  fillColor: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.06),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(
                      color: Color(0xFF0891B2),
                      width: 1.5,
                    ),
                  ),
                  errorBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(
                      color: Colors.red.shade300,
                      width: 1.5,
                    ),
                  ),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 14,
                  ),
                ),
              ),
            ),
            // Error text
            if (_errorText != null) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
                child: FadeIn(
                  child: Row(
                    children: [
                      Icon(
                        CupertinoIcons.exclamationmark_circle,
                        size: 14,
                        color: Colors.red.shade400,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          _errorText!,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.red.shade400,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 16),
            // Import button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: SizedBox(
                height: 48,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _importUrl,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0891B2),
                    foregroundColor: Colors.white,
                    disabledBackgroundColor:
                        const Color(0xFF0891B2).withOpacity(0.5),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    elevation: 0,
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: LoadingIndicatorM3E(
                            constraints: BoxConstraints(maxWidth: 22, maxHeight: 22),
                          ),
                        )
                      : Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(CupertinoIcons.arrow_down_doc,
                                size: 18),
                            const SizedBox(width: 8),
                            Text(
                              'Import Content',
                              style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.w600,
                                fontSize: 15,
                              ),
                            ),
                          ],
                        ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            // Supported types hint
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(
                'Supports web articles, blog posts, and direct PDF links',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                  fontSize: 11,
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
