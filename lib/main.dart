import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'dart:async';

import 'screens/home_screen.dart';
import 'screens/paraphraser_page.dart';
import 'screens/grammar_page.dart';
import 'screens/translator_page.dart';
import 'screens/ai_detector_page.dart';
import 'screens/summarizer_page.dart';
import 'screens/tone_editor_page.dart';
import 'screens/jade_chat_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/splash_screen.dart';
import 'screens/auth/login_screen.dart';
import 'screens/admin_panel.dart';
import 'theme/theme_provider.dart';
import 'providers/auth_provider.dart';
import 'providers/keyboard_provider.dart';
import 'theme/app_theme.dart';
import 'utils/permission_handler.dart';
import 'utils/animation_optimizer.dart';
import 'utils/frame_rate_controller.dart';
import 'utils/app_logger.dart';
import 'utils/responsive.dart';
import 'services/firebase_service.dart';
import 'services/firebase_messaging_service.dart';
import 'services/unified_ai_service.dart';
import 'services/cache_manager.dart';
import 'services/admin_service.dart';
import 'services/ai_settings_bridge.dart';
import 'services/ios_keyboard_bridge.dart';
import 'services/billing_service.dart';
import 'services/deep_link_service.dart';
import 'services/usage_analytics_service.dart';
import 'widgets/tool_popup.dart';
import 'widgets/tools_fab.dart';
import 'widgets/whats_new_sheet.dart';

// Global navigator key for app-wide navigation
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
final ValueNotifier<int?> homeTabNavigationRequest = ValueNotifier<int?>(null);

// Global service initialization status flags
bool isFirebaseInitialized = false;
bool isGroqInitialized = false;

void main() async {
  // Ensure Flutter is initialized with optimized settings
  final binding = WidgetsFlutterBinding.ensureInitialized();

  // Optimize frame scheduling for better performance
  binding.deferFirstFrame();

  // Initialize Firebase first - this is critical
  try {
    await FirebaseService.initialize().timeout(const Duration(seconds: 5));
    isFirebaseInitialized = true;
    AppLogger.init('Firebase');

    // Initialize Firebase Messaging after core Firebase is ready
    try {
      await FirebaseMessagingService().initialize().timeout(const Duration(seconds: 3));
      AppLogger.init('Firebase Messaging');
    } catch (e) {
      AppLogger.warning('Firebase Messaging initialization error: $e');
    }

    // Initialize AdminService for Cloud Functions
    AdminService.init();
    AppLogger.init('AdminService');
  } catch (e) {
    AppLogger.error('Error initializing Firebase', e);
    // Continue with app launch but some features may be limited
    isFirebaseInitialized = true;
  }

  // Set UI styles immediately. systemNavigationBar* fields only apply on Android.
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  // Set preferred orientations
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  // Initialize animation optimizer early
  unawaited(AnimationOptimizer.initialize());

  // Start monitoring frame rate to ensure smooth 60 FPS animations
  FrameRateController.instance.startMonitoring();

  // Initialize Groq in the background
  isGroqInitialized = false;
  unawaited(UnifiedAIService.initialize().then((_) {
    isGroqInitialized = true;
    AppLogger.init('Groq service');

    // Initialize AI Settings Bridge for Android native services (Android only)
    if (defaultTargetPlatform == TargetPlatform.android) {
      AISettingsBridge.initialize();
      unawaited(AISettingsBridge.syncSettingsToAndroid());
      AppLogger.init('AI Settings Bridge');
    }
    // iOS counterpart: push current API key + model into the App Group so
    // the RewordiumKeyboard extension can talk to Groq without waiting for
    // the user to open settings. Internally guarded so non-iOS is a no-op.
    if (defaultTargetPlatform == TargetPlatform.iOS) {
      unawaited(IosKeyboardBridge.syncCurrentSettings());
      AppLogger.init('iOS Keyboard Bridge');
    }
  }).catchError((e) {
    AppLogger.warning('Error initializing Groq service: $e');
    // Continue with app launch but some features may be limited
    isGroqInitialized = true;
  }));

  // Initialize Deep Link Service for app shortcuts immediately
  // (not dependent on AI service being ready)
  DeepLinkService.initialize();
  AppLogger.init('Deep Link Service');

  // Initialize keyboard provider with minimal setup
  final keyboardProvider = KeyboardProvider();
  try {
    await keyboardProvider.initializeFromPrefs().timeout(const Duration(seconds: 3));
  } catch (e) {
    AppLogger.warning('Error initializing keyboard provider: $e');
  }

  // Create auth provider after Firebase is initialized
  final authProvider = AuthProvider();

  // Create billing service and set up subscription callback
  final billingService = BillingService();
  billingService.onSubscriptionActive =
      (
    String productId,
    String? purchaseToken,
    DateTime? purchaseDate,
    bool isRestored,
  ) {
    if (purchaseToken == null || purchaseToken.isEmpty) {
      AppLogger.warning(
        'Subscription callback without purchase token for product: $productId',
      );
      return;
    }

    // Update auth provider when subscription is activated
    final normalizedProductId = productId.trim().toLowerCase();
    final planType = switch (normalizedProductId) {
      'rewordium_pro_yearly' => 'yearly',
      'rewordium_pro_monthly' => 'monthly',
      _ when normalizedProductId.contains('year') ||
              normalizedProductId.contains('annual') =>
        'yearly',
      _ => 'monthly',
    };

    unawaited(
      authProvider.activateProSubscription(
        planType: planType,
        purchaseToken: purchaseToken,
        productId: productId,
        purchaseDate: purchaseDate,
        isRestored: isRestored,
      ),
    );
  };

  // Allow frame to be drawn now that critical initialization is complete
  binding.allowFirstFrame();

  // Launch the app directly
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (context) => ThemeProvider()),
        ChangeNotifierProvider.value(value: authProvider),
        ChangeNotifierProvider.value(value: keyboardProvider),
        ChangeNotifierProvider.value(value: billingService),
      ],
      child: const MyApp(),
    ),
  );

  // Initialize remaining services in the background
  void initializeServices() {
    // Play Integrity is now handled in SplashScreen with actual enforcement

    // Initialize billing service for Google Play In-App Purchases
    billingService.initialize();
    AppLogger.init('Billing service');

    // Initialize cache manager
    try {
      CacheManager.initialize();
      AppLogger.init('Cache manager');
    } catch (e) {
      AppLogger.warning('Error initializing cache manager: $e');
    }

    // Initialize animation optimizer in a separate microtask
    Future.microtask(() async {
      try {
        await AnimationOptimizer.initialize();
        AppLogger.init('Animation optimizer');
      } catch (e) {
        AppLogger.warning('Error initializing animation optimizer: $e');
      }
    });
  }

  // Start the initialization
  initializeServices();
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangePlatformBrightness() {
    // Update theme when system brightness changes
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    themeProvider.updateSystemBrightness();
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;
    final keyboardProvider = Provider.of<KeyboardProvider>(context);

    // Ensure keyboard is enabled when app starts
    WidgetsBinding.instance.addPostFrameCallback((_) {
      keyboardProvider.ensureKeyboardEnabled(context);
    });

    final useDynamicColors = themeProvider.useDynamicColors;

    // Performance optimized app structure
    return DynamicColorBuilder(
      builder: (lightDynamic, darkDynamic) {
        final lightTheme = AppTheme.lightThemeWith(
          useDynamicColors ? lightDynamic : null,
        );
        final darkTheme = AppTheme.darkThemeWith(
          useDynamicColors ? darkDynamic : null,
        );
        final activeScheme =
            isDarkMode ? darkTheme.colorScheme : lightTheme.colorScheme;

        themeProvider.syncKeyboardAccent(activeScheme.primary);

        // Update system UI overlay style based on effective active color scheme.
        SystemChrome.setSystemUIOverlayStyle(
          SystemUiOverlayStyle(
            statusBarColor: Colors.transparent,
            statusBarIconBrightness:
                isDarkMode ? Brightness.light : Brightness.dark,
            statusBarBrightness:
                isDarkMode ? Brightness.dark : Brightness.light,
            systemNavigationBarColor: activeScheme.surfaceContainerLow,
            systemNavigationBarIconBrightness:
                isDarkMode ? Brightness.light : Brightness.dark,
          ),
        );

        return MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'Rewordium',
          theme: lightTheme,
          darkTheme: darkTheme,
          themeMode: themeProvider.themeMode,
          navigatorKey: navigatorKey,
          home: const SplashScreen(),
          routes: {
            '/home': (context) => const HomePage(),
            '/settings': (context) => const SettingsScreen(),
            '/admin': (context) => const AdminPanel(),
          },
          // Performance optimizations
          builder: (context, child) {
            // Apply global performance optimizations to the entire app
            return MediaQuery(
              // Avoid unnecessary rebuilds when keyboard appears
              data: MediaQuery.of(context).copyWith(textScaleFactor: 1.0),
              child: RepaintBoundary(
                child: child!,
              ),
            );
          },
          // Optimize scrolling performance, but keep iOS-native bounce so
          // lists/sheets feel right on iPhone. Android stays Material-style.
          scrollBehavior: _AdaptiveScrollBehavior(),
        );
      },
    );
  }
}

// Scrollable physics that match each platform's native expectation. iOS gets
// bounce, Android gets glow-overscroll. Scrollbars stay off everywhere.
class _AdaptiveScrollBehavior extends MaterialScrollBehavior {
  @override
  Widget buildScrollbar(
      BuildContext context, Widget child, ScrollableDetails details) {
    return child;
  }

  @override
  Widget buildOverscrollIndicator(
      BuildContext context, Widget child, ScrollableDetails details) {
    // No glow on Android, no bounce-stretch indicator anywhere — matches the
    // original scrollBehavior(overscroll: false). Physics still bounce on iOS.
    return child;
  }

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) {
    final platform = getPlatform(context);
    if (platform == TargetPlatform.iOS || platform == TargetPlatform.macOS) {
      return const BouncingScrollPhysics();
    }
    return const ClampingScrollPhysics();
  }
}

// Helper method to build system keyboard overlay if needed
Widget buildSystemKeyboardOverlay(BuildContext context) {
  return Consumer<KeyboardProvider>(
    builder: (context, provider, child) {
      // Show the system keyboard overlay when enabled
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (provider.isSystemKeyboardEnabled) {
          provider.showSystemKeyboardOverlay(context);
        }
      });

      // Return an empty container as the overlay is managed by the KeyboardService
      return Container();
    },
  );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with TickerProviderStateMixin, WidgetsBindingObserver {
  int _selectedIndex = 0;
  late TabController _tabController;
  final PermissionHandler _permissionHandler = PermissionHandler();

  final List<Widget> _pages = const [
    HomeScreen(),
    ParaphraserPage(),
    GrammarPage(),
    SettingsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _tabController = TabController(length: 4, vsync: this);
    _tabController.addListener(_handleTabChange);
    homeTabNavigationRequest.addListener(_handleTabNavigationRequest);

    // Request permissions on app start
    _requestPermissions();

    // Show What's New sheet if app was updated
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) WhatsNewSheet.showIfNeeded(context);
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      UsageAnalyticsService.touchUserActivity();
      DeepLinkService.setNavigationReady();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Forward lifecycle changes to KeyboardProvider so it can pause/resume
    // background timers for battery efficiency.
    final kbProvider = context.read<KeyboardProvider>();
    kbProvider.onAppLifecycleChange(state);
  }

  Future<void> _requestPermissions() async {
    await _permissionHandler.requestNotificationPermission();
  }

  void _handleTabNavigationRequest() {
    final requestedTab = homeTabNavigationRequest.value;
    if (requestedTab == null) return;

    if (requestedTab >= 0 && requestedTab < _pages.length) {
      _onItemTapped(requestedTab);
    }
    homeTabNavigationRequest.value = null;
  }

  void _handleTabChange() {
    if (!_tabController.indexIsChanging) {
      setState(() {
        _selectedIndex = _tabController.index;
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    homeTabNavigationRequest.removeListener(_handleTabNavigationRequest);
    _tabController.removeListener(_handleTabChange);
    _tabController.dispose();
    super.dispose();
  }

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
      _tabController.animateTo(index);
    });
  }


  Widget _buildToolsFab(BuildContext context) {
    return ToolsFab(
      onSelectHomeTab: _onItemTapped,
      tooltip: 'Tools',
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final authProvider = Provider.of<AuthProvider>(context);
    final r = Responsive.of(context);

    if (!authProvider.isLoggedIn) {
      return const LoginScreen();
    }

    return Scaffold(
      body: TabBarView(
        controller: _tabController,
        physics: const NeverScrollableScrollPhysics(),
        children: _pages,
      ),
      floatingActionButton: _buildToolsFab(context),
      bottomNavigationBar: Container(
        margin: EdgeInsets.fromLTRB(r.w(12), 0, r.w(12), r.h(8)),
        decoration: BoxDecoration(
          color: colorScheme.surface,
          borderRadius: BorderRadius.circular(r.r(28)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.12),
              blurRadius: 16,
              offset: const Offset(0, 4),
            ),
          ],
          border: Border.all(
            color: colorScheme.outlineVariant.withValues(alpha: 0.3),
          ),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(r.r(28)),
          child: NavigationBarM3E(
            selectedIndex: _selectedIndex,
            onDestinationSelected: _onItemTapped,
            indicatorStyle: NavBarM3EIndicatorStyle.pill,
            shapeFamily: NavBarM3EShapeFamily.round,
            size: NavBarM3ESize.medium,
            labelBehavior: NavBarM3ELabelBehavior.alwaysShow,
            backgroundColor: colorScheme.surface,
            indicatorColor: colorScheme.secondaryContainer,
            destinations: const [
              NavigationDestinationM3E(
                icon: Icon(CupertinoIcons.home),
                selectedIcon: Icon(CupertinoIcons.house_fill),
                label: 'Home',
              ),
              NavigationDestinationM3E(
                icon: Icon(CupertinoIcons.pencil_outline),
                selectedIcon: Icon(CupertinoIcons.pencil_outline),
                label: 'Paraphrase',
              ),
              NavigationDestinationM3E(
                icon: Icon(CupertinoIcons.checkmark_seal),
                selectedIcon: Icon(CupertinoIcons.checkmark_seal_fill),
                label: 'Grammar',
              ),
              NavigationDestinationM3E(
                icon: Icon(CupertinoIcons.gear),
                selectedIcon: Icon(CupertinoIcons.gear_solid),
                label: 'Settings',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
