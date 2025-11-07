class UserModel {
  final String uid;
  final String name;
  final String email;
  final bool isPro;
  final int credits;
  final DateTime createdAt;
  final DateTime? lastCreditRefresh;
  final String signInMethod;
  final String? fcmToken;
  final bool isActive;

  UserModel({
    required this.uid,
    required this.name,
    required this.email,
    required this.isPro,
    required this.credits,
    required this.createdAt,
    this.lastCreditRefresh,
    required this.signInMethod,
    this.fcmToken,
    this.isActive = true,
  });

  factory UserModel.fromMap(String uid, Map<String, dynamic> data) {
    return UserModel(
      uid: uid,
      name: data['name'] ?? '',
      email: data['email'] ?? '',
      isPro: data['isPro'] ?? false,
      credits: data['credits'] ?? 0,
      createdAt: data['createdAt']?.toDate() ?? DateTime.now(),
      lastCreditRefresh: data['lastCreditRefresh']?.toDate(),
      signInMethod: data['signInMethod'] ?? 'unknown',
      fcmToken: data['fcmToken'],
      isActive: data['isActive'] ?? true,
    );
  }

  factory UserModel.fromDocumentSnapshot(String uid, Object? data) {
    final map = data as Map<String, dynamic>;
    return UserModel.fromMap(uid, map);
  }

  Map<String, dynamic> toMap() {
    return {
      'name': name,
      'email': email,
      'isPro': isPro,
      'credits': credits,
      'createdAt': createdAt,
      'lastCreditRefresh': lastCreditRefresh,
      'signInMethod': signInMethod,
      'fcmToken': fcmToken,
      'isActive': isActive,
    };
  }

  UserModel copyWith({
    String? name,
    String? email,
    bool? isPro,
    int? credits,
    DateTime? createdAt,
    DateTime? lastCreditRefresh,
    String? signInMethod,
    String? fcmToken,
    bool? isActive,
  }) {
    return UserModel(
      uid: uid,
      name: name ?? this.name,
      email: email ?? this.email,
      isPro: isPro ?? this.isPro,
      credits: credits ?? this.credits,
      createdAt: createdAt ?? this.createdAt,
      lastCreditRefresh: lastCreditRefresh ?? this.lastCreditRefresh,
      signInMethod: signInMethod ?? this.signInMethod,
      fcmToken: fcmToken ?? this.fcmToken,
      isActive: isActive ?? this.isActive,
    );
  }

  String get userType => isPro ? 'Pro' : 'Free';
  String get status => isActive ? 'Active' : 'Inactive';
}
