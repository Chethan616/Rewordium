import 'dart:io';

void main() {
  var file = File('lib/providers/auth_provider.dart');
  var content = file.readAsStringSync();

  content = content.replaceAll('int? _credits;', 'int? _credits;\n  DateTime? _expiryDate;');
  content = content.replaceAll('int? get credits => _credits;', 'int? get credits => _credits;\n  DateTime? get expiryDate => _expiryDate;');

  content = content.replaceAll(RegExp(r'bool get isSubscriptionExpiringSoon \{[\s\S]*?return false; \/\/ Placeholder\n  \}'), '''bool get isSubscriptionExpiringSoon {
    if (!_isPro || _planType == 'onetime' || _expiryDate == null) return false;
    return _expiryDate!.difference(DateTime.now()).inDays <= 7 && _expiryDate!.difference(DateTime.now()).inDays >= 0;
  }''');

  content = content.replaceAll(RegExp(r'int\? get daysUntilExpiry \{[\s\S]*?return null; \/\/ Placeholder\n  \}'), '''int? get daysUntilExpiry {
    if (!_isPro || _planType == 'onetime' || _expiryDate == null) return null;
    return _expiryDate!.difference(DateTime.now()).inDays;
  }''');

  content = content.replaceAll(\"final expiryDate = _parseSubscriptionExpiry(subData?['expiryDate']);\", \"final expiryDate = _parseSubscriptionExpiry(subData?['expiryDate']);\n        _expiryDate = expiryDate;\");

  content = content.replaceAll(RegExp(r\"if \(expiryDate != null && _planType != 'onetime'\) \{\n\s*DateTime expiry;\n\s*if \(expiryDate is String\) \{\n\s*expiry = DateTime.parse\(expiryDate\);\n\s*\} else if \(expiryDate is Timestamp\) \{\n\s*expiry = expiryDate.toDate\(\);\n\s*\} else \{\n\s*expiry = DateTime.now\(\)\n\s*\.subtract\(const Duration\(days: 1\)\); \/\/ Force expiry\n\s*\}\"), '''if (expiryDate != null && _planType != 'onetime') {
            DateTime expiry;
            if (expiryDate is String) {
              expiry = DateTime.parse(expiryDate);
            } else if (expiryDate is Timestamp) {
              expiry = expiryDate.toDate();
            } else {
              expiry = DateTime.now()
                  .subtract(const Duration(days: 1)); // Force expiry
            }
            _expiryDate = expiry;''');

  file.writeAsStringSync(content);
}
