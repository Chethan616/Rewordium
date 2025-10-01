import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'dart:async';

import 'screens/home_screen.dart';
import 'screens/paraphraser_page.dart';
import 'screens/grammar_page.dart';
import 'screens/settings_screen.dart';
import 'screens/splash_screen.dart';
import 'theme/theme_provider.dart';
import 'providers/auth_provider.dart';
import 'providers/keyboard_provider.dart';
import 'theme/app_theme.dart';
import 'utils/permission_handler.dart';
import 'utils/animation_optimizer.dart';
import 'utils/frame_rate_controller.dart';
import 'services/firebase_service.dart';
import 'services/firebase_messaging_service.dart';
import 'services/groq_service.dart';
import 'services/cache_manager.dart';
import 'services/admin_service.dart';
import 'widgets/tool_popup.dart';
import 'admin.dart';

// Global navigator key for app-wide navigation
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

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
    await FirebaseService.initialize();
    isFirebaseInitialized = true;
    debugPrint('Firebase initialized successfully');

    // Initialize Firebase Messaging after core Firebase is ready
    try {
      await FirebaseMessagingService().initialize();
      debugPrint('Firebase Messaging initialized');
    } catch (e) {
      debugPrint('Firebase Messaging initialization error: $e');
    }

    // Initialize AdminService for Cloud Functions
    AdminService.init();
    debugPrint('AdminService initialized');
  } catch (e) {
  } catch (e) {
    debugPrint('Error initializing Firebase: $e');
    // Continue with app launch but some features may be limited
    isFirebaseInitialized = true;
  }

  // Set UI styles immediately
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
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
  unawaited(GroqService.initialize().then((_) {
    isGroqInitialized = true;
    debugPrint('Groq service initialized successfully');
  }).catchError((e) {
    debugPrint('Error initializing Groq service: $e');
    // Continue with app launch but some features may be limited
    isGroqInitialized = true;
  }));

  // Initialize keyboard provider with minimal setup
  final keyboardProvider = KeyboardProvider();
  try {
    await keyboardProvider.initializeFromPrefs();
  } catch (e) {
    debugPrint('Error initializing keyboard provider: $e');
  }

  // Create auth provider after Firebase is initialized
  final authProvider = AuthProvider();

  // Allow frame to be drawn now that critical initialization is complete
  binding.allowFirstFrame();

  // Launch the app directly
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (context) => ThemeProvider()),
        ChangeNotifierProvider.value(value: authProvider),
        ChangeNotifierProvider.value(value: keyboardProvider),
      ],
      child: const MyApp(),
    ),
  );

  // Initialize remaining services in the background
  void initializeServices() {
    // Initialize cache manager
    try {
      CacheManager.initialize();
      debugPrint('Cache manager initialized');
    } catch (e) {
      debugPrint('Error initializing cache manager: $e');
    }

    // Initialize animation optimizer in a separate microtask
    Future.microtask(() async {
      try {
        await AnimationOptimizer.initialize();
        debugPrint('Animation optimizer initialized');
      } catch (e) {
        debugPrint('Error initializing animation optimizer: $e');
      }
    });
  }

  // Start the initialization
  initializeServices();
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;
    final keyboardProvider = Provider.of<KeyboardProvider>(context);

    // Ensure keyboard is enabled when app starts
    WidgetsBinding.instance.addPostFrameCallback((_) {
      keyboardProvider.ensureKeyboardEnabled(context);
    });

    // Update system UI overlay style based on theme
    SystemChrome.setSystemUIOverlayStyle(
      SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness:
            isDarkMode ? Brightness.light : Brightness.dark,
        systemNavigationBarColor:
            isDarkMode ? AppTheme.darkCardColor : Colors.white,
        systemNavigationBarIconBrightness:
            isDarkMode ? Brightness.light : Brightness.dark,
      ),
    );

    // Performance optimized app structure
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Rewordium',
      theme: themeProvider.theme,
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
      // Optimize scrolling performance
      scrollBehavior: const MaterialScrollBehavior().copyWith(
        scrollbars: false,
        overscroll: false,
        physics: const ClampingScrollPhysics(),
      ),
    );
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

class _HomePageState extends State<HomePage> with TickerProviderStateMixin {
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
    _tabController = TabController(length: 4, vsync: this);
    _tabController.addListener(_handleTabChange);

    // Request permissions on app start
    _requestPermissions();
  }

  Future<void> _requestPermissions() async {
    await _permissionHandler.requestCameraPermission();
    await _permissionHandler.requestMicrophonePermission();
    await _permissionHandler.requestPhotosPermission();
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

  void _showToolPopup() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => const ToolPopup(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;

    return Scaffold(
      body: TabBarView(
        controller: _tabController,
        physics: const NeverScrollableScrollPhysics(),
        children: _pages,
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showToolPopup,
        backgroundColor: AppTheme.primaryColor,
        child: const Icon(CupertinoIcons.square_grid_2x2, color: Colors.white),
      ),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: AppTheme.cardColor,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(isDarkMode ? 0.2 : 0.05),
              blurRadius: 10,
              offset: const Offset(0, -5),
            ),
          ],
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: 8.0,
              vertical: 8.0,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildNavItem(0, CupertinoIcons.home, 'Home'),
                _buildNavItem(1, CupertinoIcons.pencil_outline, 'Paraphrase'),
                _buildNavItem(2, CupertinoIcons.checkmark_seal, 'Grammar'),
                _buildNavItem(3, CupertinoIcons.gear, 'Settings'),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, String label) {
    final isSelected = _selectedIndex == index;

    return GestureDetector(
      onTap: () => _onItemTapped(index),
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: isSelected
            ? BoxDecoration(
                color: AppTheme.primaryColor.withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
              )
            : null,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: isSelected
                  ? AppTheme.primaryColor
                  : AppTheme.textSecondaryColor,
              size: 24,
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                color: isSelected
                    ? AppTheme.primaryColor
                    : AppTheme.textSecondaryColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
