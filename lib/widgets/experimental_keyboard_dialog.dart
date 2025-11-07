import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

/// Shows an experimental feature dialog for keyboard settings
/// Returns true if user wants to proceed, false otherwise
Future<bool> showExperimentalKeyboardDialog(BuildContext context) async {
  final shouldProceed = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (BuildContext context) {
      return AlertDialog(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(Icons.science_outlined, color: Colors.orange, size: 28),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Experimental',
                    style: TextStyle(fontSize: 20),
                  ),
                ),
              ],
            ),
            Padding(
              padding: EdgeInsets.only(
                  left: 40), // Align with text above (28px icon + 12px spacing)
              child: Text(
                'Feature',
                style: TextStyle(fontSize: 20),
              ),
            ),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '⚠️ Development Notice',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
              SizedBox(height: 12),
              Text(
                'This keyboard is still in active development. We\'re working hard to perfect it!',
                style: TextStyle(height: 1.5),
              ),
              SizedBox(height: 16),
              Container(
                padding: EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.blue.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.blue.withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '🎯 Our Mission',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Colors.blue,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Build a powerful AI keyboard with:\n• Privacy & Security First\n• Lightning Fast Performance\n• Advanced AI Features',
                      style: TextStyle(height: 1.5),
                    ),
                  ],
                ),
              ),
              SizedBox(height: 16),
              Container(
                padding: EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.green.withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '💝 Free & Open Development',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Colors.green,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'We\'re not charging anything for this keyboard. Building a quality keyboard implementation is incredibly challenging work!',
                      style: TextStyle(height: 1.5),
                    ),
                  ],
                ),
              ),
              SizedBox(height: 16),
              Container(
                padding: EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.purple.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.purple.withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '❤️ Support Us',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Colors.purple,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'If you appreciate our work and want to support development:',
                      style: TextStyle(height: 1.5),
                    ),
                    SizedBox(height: 8),
                    InkWell(
                      onTap: () async {
                        final uri =
                            Uri.parse('https://www.rewordium.tech/donate');
                        if (await canLaunchUrl(uri)) {
                          await launchUrl(uri,
                              mode: LaunchMode.externalApplication);
                        }
                      },
                      child: Container(
                        padding:
                            EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: Colors.purple,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.favorite, color: Colors.white, size: 16),
                            SizedBox(width: 8),
                            Text(
                              'Donate Here',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            SizedBox(width: 4),
                            Icon(Icons.open_in_new,
                                color: Colors.white, size: 14),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
            child: Text('Continue to Enable'),
          ),
        ],
      );
    },
  );

  return shouldProceed ?? false;
}
