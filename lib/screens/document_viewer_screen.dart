import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:animate_do/animate_do.dart';
import '../theme/app_theme.dart';
import '../models/document_result.dart';
import '../services/export_service.dart';

/// Document viewer screen with text view and external PDF open.
/// Shows extracted text (selectable) with "Use This Text" and "Open in Viewer" buttons.
class DocumentViewerScreen extends StatefulWidget {
  final DocumentResult document;

  /// Called when user taps "Use This Text" — sends text back to calling tool screen.
  final void Function(String text)? onUseText;

  /// Optional processed/result text to display alongside original.
  final String? processedText;

  /// Label for the processed text tab (e.g. "Paraphrased", "Translated").
  final String? processedLabel;

  const DocumentViewerScreen({
    super.key,
    required this.document,
    this.onUseText,
    this.processedText,
    this.processedLabel,
  });

  @override
  State<DocumentViewerScreen> createState() => _DocumentViewerScreenState();
}

class _DocumentViewerScreenState extends State<DocumentViewerScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  late List<_TabDef> _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = _buildTabs();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  List<_TabDef> _buildTabs() {
    final tabs = <_TabDef>[];
    // Tab 1: Extracted text (always shown)
    tabs.add(_TabDef(icon: CupertinoIcons.doc_text, label: 'Text'));
    // Tab 2: Processed text (if available)
    if (widget.processedText != null && widget.processedText!.isNotEmpty) {
      tabs.add(_TabDef(
        icon: CupertinoIcons.sparkles,
        label: widget.processedLabel ?? 'Result',
      ));
    }
    return tabs;
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _showExportSheet() {
    ExportService.showExportSheet(
      context,
      title: widget.document.title ?? 'Document',
      content: widget.processedText ?? widget.document.text,
      originalText: widget.processedText != null ? widget.document.text : null,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(CupertinoIcons.back),
          color: AppTheme.textPrimaryColor,
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.document.title ?? 'Document',
              style: AppTheme.bodyLarge.copyWith(
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            Text(
              widget.document.summary,
              style: AppTheme.bodySmall.copyWith(
                color: AppTheme.textSecondaryColor,
                fontSize: 11,
              ),
            ),
          ],
        ),
        actions: [
          // Open in external viewer (for PDFs with a file path)
          if (widget.document.type == DocumentType.pdf &&
              widget.document.filePath != null)
            IconButton(
              icon: const Icon(CupertinoIcons.arrow_up_doc, size: 20),
              color: AppTheme.textPrimaryColor,
              onPressed: () {
                OpenFilex.open(widget.document.filePath!);
              },
              tooltip: 'Open in viewer',
            ),
          IconButton(
            icon: const Icon(CupertinoIcons.share, size: 20),
            color: AppTheme.textPrimaryColor,
            onPressed: _showExportSheet,
            tooltip: 'Export / Share',
          ),
        ],
        bottom: _tabs.length > 1
            ? PreferredSize(
                preferredSize: const Size.fromHeight(46),
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  decoration: BoxDecoration(
                    color: AppTheme.textSecondaryColor.withOpacity(0.06),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: TabBar(
                    controller: _tabController,
                    indicator: BoxDecoration(
                      color: AppTheme.primaryColor,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    indicatorSize: TabBarIndicatorSize.tab,
                    labelColor: Colors.white,
                    unselectedLabelColor: AppTheme.textSecondaryColor,
                    labelStyle: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                    unselectedLabelStyle: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                    ),
                    dividerColor: Colors.transparent,
                    tabs: _tabs
                        .map((t) => Tab(
                              height: 38,
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(t.icon, size: 14),
                                  const SizedBox(width: 6),
                                  Flexible(
                                    child: Text(
                                      t.label,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ))
                        .toList(),
                  ),
                ),
              )
            : null,
      ),
      body: _tabs.length > 1
          ? TabBarView(
              controller: _tabController,
              children: _tabs.map((t) => _buildTabContent(t)).toList(),
            )
          : _buildTextTab(widget.document.text, isOriginal: true),
      // "Use This Text" floating button
      bottomNavigationBar: widget.onUseText != null
          ? SafeArea(
              child: FadeInUp(
                duration: const Duration(milliseconds: 400),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                  child: SizedBox(
                    height: 50,
                    child: ElevatedButton(
                      onPressed: () {
                        widget.onUseText!(widget.document.text);
                        Navigator.of(context).pop();
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        elevation: 0,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(CupertinoIcons.text_cursor, size: 18),
                          const SizedBox(width: 8),
                          Text(
                            'Use This Text',
                            style: AppTheme.bodyMedium.copyWith(
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
              ),
            )
          : null,
    );
  }

  Widget _buildTabContent(_TabDef tab) {
    if (tab.label == 'Text') {
      return _buildTextTab(widget.document.text, isOriginal: true);
    } else {
      return _buildTextTab(widget.processedText!, isOriginal: false);
    }
  }

  Widget _buildTextTab(String text, {required bool isOriginal}) {
    return Column(
      children: [
        // Word count + copy bar
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: AppTheme.textSecondaryColor.withOpacity(0.1),
              ),
            ),
          ),
          child: Row(
            children: [
              Icon(
                CupertinoIcons.text_alignleft,
                size: 14,
                color: AppTheme.textSecondaryColor,
              ),
              const SizedBox(width: 6),
              Text(
                '${text.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length} words',
                style: AppTheme.bodySmall.copyWith(
                  color: AppTheme.textSecondaryColor,
                  fontSize: 12,
                ),
              ),
              const Spacer(),
              GestureDetector(
                onTap: () {
                  Clipboard.setData(ClipboardData(text: text));
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Copied to clipboard'),
                      duration: Duration(seconds: 2),
                    ),
                  );
                },
                child: Row(
                  children: [
                    Icon(
                      CupertinoIcons.doc_on_clipboard,
                      size: 14,
                      color: AppTheme.primaryColor,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      'Copy All',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppTheme.primaryColor,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        // Scrollable text content
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: SelectableText(
              text,
              style: AppTheme.bodyMedium.copyWith(
                fontSize: 14,
                height: 1.6,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _TabDef {
  final IconData icon;
  final String label;
  const _TabDef({required this.icon, required this.label});
}
