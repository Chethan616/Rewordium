import 'package:flutter/material.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../services/admin_service.dart';
import '../models/user_model.dart';

class AdminPanel extends StatefulWidget {
  const AdminPanel({super.key});

  @override
  State<AdminPanel> createState() => _AdminPanelState();
}

class _AdminPanelState extends State<AdminPanel> with TickerProviderStateMixin {
  late TabController _tabController;
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _bodyController = TextEditingController();
  final TextEditingController _searchController = TextEditingController();

  bool _isAuthenticated = false;
  bool _isLoading = false;
  String? _statusMessage;
  bool _statusIsError = false;

  List<UserModel> _users = [];
  List<UserModel> _filteredUsers = [];
  Map<String, int> _userStats = {
    'total': 0,
    'pro': 0,
    'free': 0,
    'activeNow': 0,
    'dau': 0,
  };
  Map<String, dynamic> _revenueStats = {};
  Map<String, dynamic> _apiUsageStats = {};
  List<Map<String, dynamic>> _recentTransactions = [];
  // Hardcoded baseline: Only count revenue from January 16, 2026 onwards
  final DateTime _revenueBaseline = DateTime(2026, 1, 16);

  // Filter and sort state
  String _filterBy = 'all'; // 'all', 'pro', 'free', 'news_subscribers'
  String _sortBy = 'createdAt'; // 'name', 'email', 'createdAt', 'credits'
  bool _sortAscending = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 6, vsync: this);
    _checkAdminAccess();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _passwordController.dispose();
    _titleController.dispose();
    _bodyController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _checkAdminAccess() {
    if (AdminService.isAdmin()) {
      setState(() => _isAuthenticated = true);
      _loadData();
    }
  }

  Future<void> _loadData() async {
    if (!_isAuthenticated) return;

    setState(() => _isLoading = true);

    try {
      final users = await AdminService.getAllUsers();
      final stats = await AdminService.getUserStats();
      final revenueStats =
          await AdminService.getRevenueStats(from: _revenueBaseline);
      final apiUsageStats = await AdminService.getApiUsageStats();
      final transactions =
          await AdminService.getRecentTransactions(from: _revenueBaseline);

      setState(() {
        _users = users;
        _filteredUsers = users;
        _userStats = stats;
        _revenueStats = revenueStats;
        _apiUsageStats = apiUsageStats;
        _recentTransactions = transactions;
      });
    } catch (e) {
      _showStatus('Error loading data: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  // Revenue baseline is hardcoded to January 16, 2026 - no dynamic controls needed

  void _authenticate() {
    final password = _passwordController.text.trim();

    if (!AdminService.isAdmin()) {
      _showStatus('Access denied: Not an admin account', isError: true);
      return;
    }

    if (AdminService.verifyAdminPassword(password)) {
      setState(() {
        _isAuthenticated = true;
        _passwordController.clear();
      });
      _loadData();
    } else {
      _showStatus('Invalid password', isError: true);
    }
  }

  void _showStatus(String message, {bool isError = false}) {
    setState(() {
      _statusMessage = message;
      _statusIsError = isError;
    });

    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) {
        setState(() => _statusMessage = null);
      }
    });
  }

  void _filterUsers(String query) {
    // Use the async search method for better performance
    _searchUsers(query);
  }

  Future<void> _sendNotification(String target, {String? userId}) async {
    if (_titleController.text.trim().isEmpty ||
        _bodyController.text.trim().isEmpty) {
      _showStatus('Please enter both title and message', isError: true);
      return;
    }

    setState(() => _isLoading = true);

    try {
      bool success = false;

      switch (target) {
        case 'all':
          success = await AdminService.sendNotificationToAllUsers(
            title: _titleController.text.trim(),
            body: _bodyController.text.trim(),
          );
          break;
        case 'pro':
          success = await AdminService.sendNotificationToProUsers(
            title: _titleController.text.trim(),
            body: _bodyController.text.trim(),
          );
          break;
        case 'free':
          success = await AdminService.sendNotificationToFreeUsers(
            title: _titleController.text.trim(),
            body: _bodyController.text.trim(),
          );
          break;
        case 'individual':
          if (userId != null) {
            success = await AdminService.sendNotificationToUser(
              userId: userId,
              title: _titleController.text.trim(),
              body: _bodyController.text.trim(),
            );
          }
          break;
      }

      if (success) {
        _showStatus('Notification sent successfully!');
        _titleController.clear();
        _bodyController.clear();
      } else {
        _showStatus('Failed to send notification', isError: true);
      }
    } catch (e) {
      _showStatus('Error: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!AdminService.isAdmin()) {
      return _buildAccessDenied();
    }

    if (!_isAuthenticated) {
      return _buildPasswordEntry();
    }

    return _buildAdminDashboard();
  }

  Widget _buildAccessDenied() {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Panel'),
        elevation: 0,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.admin_panel_settings,
              size: 64,
              color: Theme.of(context).colorScheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              'Access Denied',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Theme.of(context).colorScheme.error,
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 8),
            const Text('You do not have permission to access this panel.'),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Go Back'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPasswordEntry() {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Authentication'),
        elevation: 0,
      ),
      body: Center(
        child: Container(
          constraints: const BoxConstraints(maxWidth: 400),
          padding: const EdgeInsets.all(24),
          child: Card(
            elevation: 8,
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.security,
                    size: 64,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    'Admin Panel Access',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 8),
                  const Text('Enter admin password to continue'),
                  const SizedBox(height: 32),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Admin Password',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.lock),
                    ),
                    onSubmitted: (_) => _authenticate(),
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _authenticate,
                      icon: const Icon(Icons.login),
                      label: const Text('Authenticate'),
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                      ),
                    ),
                  ),
                  if (_statusMessage != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: _statusIsError
                            ? Theme.of(context).colorScheme.errorContainer
                            : Theme.of(context).colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _statusMessage!,
                        style: TextStyle(
                          color: _statusIsError
                              ? Theme.of(context).colorScheme.onErrorContainer
                              : Theme.of(context)
                                  .colorScheme
                                  .onPrimaryContainer,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAdminDashboard() {
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: colorScheme.surfaceContainerLow,
      appBar: AppBar(
        title: Row(
          children: [
            Icon(Icons.admin_panel_settings,
                size: 24, color: colorScheme.onPrimary),
            const SizedBox(width: 12),
            Text('Admin Dashboard',
                style: TextStyle(color: colorScheme.onPrimary)),
          ],
        ),
        elevation: 0,
        backgroundColor: colorScheme.primary,
        foregroundColor: colorScheme.onPrimary,
        iconTheme: IconThemeData(color: colorScheme.onPrimary),
        actionsIconTheme: IconThemeData(color: colorScheme.onPrimary),
        actions: [
          IconButton(
            icon: Icon(Icons.refresh, color: colorScheme.onPrimary),
            onPressed: _loadData,
            tooltip: 'Refresh Data',
          ),
          IconButton(
            icon: Icon(Icons.logout, color: colorScheme.onPrimary),
            onPressed: () {
              setState(() {
                _isAuthenticated = false;
                _passwordController.clear();
              });
            },
            tooltip: 'Sign Out',
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          indicatorColor: colorScheme.onPrimary,
          indicatorWeight: 3,
          labelColor: colorScheme.onPrimary,
          unselectedLabelColor: colorScheme.onPrimary.withValues(alpha: 0.7),
          labelStyle: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 13,
              color: colorScheme.onPrimary),
          unselectedLabelStyle: TextStyle(
              fontWeight: FontWeight.w400,
              color: colorScheme.onPrimary.withValues(alpha: 0.7)),
          tabs: [
            Tab(
                icon:
                    Icon(Icons.dashboard_rounded, color: colorScheme.onPrimary),
                text: 'Overview'),
            Tab(
                icon: Icon(Icons.attach_money_rounded,
                    color: colorScheme.onPrimary),
                text: 'Revenue'),
            Tab(
                icon: Icon(Icons.api_rounded, color: colorScheme.onPrimary),
                text: 'API Usage'),
            Tab(
                icon: Icon(Icons.send_rounded, color: colorScheme.onPrimary),
                text: 'Notifications'),
            Tab(
                icon: Icon(Icons.people_rounded, color: colorScheme.onPrimary),
                text: 'Users'),
            Tab(
                icon: Icon(Icons.history_rounded, color: colorScheme.onPrimary),
                text: 'History'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildOverviewTab(),
          _buildRevenueTab(),
          _buildApiUsageTab(),
          _buildNotificationsTab(),
          _buildUsersTab(),
          _buildHistoryTab(),
        ],
      ),
    );
  }

  Widget _buildOverviewTab() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Welcome Header Card
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '👋 Welcome, Admin',
                        style: Theme.of(context)
                            .textTheme
                            .headlineSmall
                            ?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: Theme.of(context).colorScheme.onPrimary,
                            ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Here\'s your app overview for today',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onPrimary
                                  .withValues(alpha: 0.85),
                            ),
                      ),
                    ],
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: _loadData,
                  icon: _isLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: LoadingIndicatorM3E(
                            constraints:
                                BoxConstraints(maxWidth: 16, maxHeight: 16),
                          ),
                        )
                      : const Icon(Icons.refresh, size: 18),
                  label: Text(_isLoading ? 'Loading...' : 'Refresh'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.onPrimary,
                    foregroundColor: Theme.of(context).colorScheme.primary,
                    elevation: 0,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 20, vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Section Title
          Text(
            '📊 User Statistics',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: Theme.of(context).colorScheme.onSurface,
                ),
          ),
          const SizedBox(height: 16),
          if (_isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  children: [
                    LoadingIndicatorM3E(),
                    SizedBox(height: 16),
                    Text('Loading data...'),
                  ],
                ),
              ),
            )
          else ...[
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    'Total Users',
                    _userStats['total'].toString(),
                    Icons.people,
                    Colors.blue,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildStatCard(
                    'Pro Users',
                    _userStats['pro'].toString(),
                    Icons.star,
                    Colors.orange,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    'Free Users',
                    _userStats['free'].toString(),
                    Icons.person,
                    Theme.of(context).colorScheme.primary,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildStatCard(
                    'Active Now',
                    (_userStats['activeNow'] ?? 0).toString(),
                    Icons.radio_button_checked,
                    Colors.red,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    'Daily Active Users',
                    (_userStats['dau'] ?? 0).toString(),
                    Icons.today,
                    Colors.indigo,
                  ),
                ),
                const SizedBox(width: 16),
                const Expanded(child: SizedBox.shrink()),
              ],
            ),
            const SizedBox(height: 32),
            Text(
              '⚡ Quick Actions',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                _buildQuickActionCard(
                  'API Usage',
                  Icons.api,
                  Colors.deepPurple,
                  () => _tabController.animateTo(2),
                ),
                const SizedBox(width: 12),
                _buildQuickActionCard(
                  'Broadcast',
                  Icons.campaign,
                  Colors.purple,
                  () => _tabController.animateTo(3),
                ),
                const SizedBox(width: 12),
                _buildQuickActionCard(
                  'Users',
                  Icons.manage_accounts,
                  Colors.teal,
                  () => _tabController.animateTo(4),
                ),
                const SizedBox(width: 12),
                _buildQuickActionCard(
                  'History',
                  Icons.history,
                  Theme.of(context).colorScheme.primary,
                  () => _tabController.animateTo(5),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStatCard(
      String title, String value, IconData icon, Color color) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: color, size: 28),
            ),
            const SizedBox(height: 16),
            Text(
              value,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: color,
                    letterSpacing: -0.5,
                  ),
            ),
            const SizedBox(height: 4),
            Text(
              title,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickActionCard(
    String title,
    IconData icon,
    Color color,
    VoidCallback onTap,
  ) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Expanded(
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                  color: Theme.of(context).colorScheme.outlineVariant),
            ),
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(icon, color: color, size: 32),
                ),
                const SizedBox(height: 16),
                Text(
                  title,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                        color: Theme.of(context).colorScheme.onSurface,
                      ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRevenueTab() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final totalRevenue = _revenueStats['totalRevenue'] ?? 0.0;
    final mrr = _revenueStats['mrr'] ?? 0.0;
    final thisMonthRevenue = _revenueStats['thisMonthRevenue'] ?? 0.0;
    final lastMonthRevenue = _revenueStats['lastMonthRevenue'] ?? 0.0;
    final revenueGrowth = _revenueStats['revenueGrowth'] ?? 0.0;
    final monthlySubscribers = _revenueStats['monthlySubscribers'] ?? 0;
    final yearlySubscribers = _revenueStats['yearlySubscribers'] ?? 0;
    final onetimeSubscribers = _revenueStats['onetimeSubscribers'] ?? 0;
    final activeSubscriptions = _revenueStats['activeSubscriptions'] ?? 0;
    final expiredSubscriptions = _revenueStats['expiredSubscriptions'] ?? 0;
    final newSubscribersThisMonth =
        _revenueStats['newSubscribersThisMonth'] ?? 0;
    final conversionRate = _revenueStats['conversionRate'] ?? 0.0;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Revenue Header Card
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '💰 Revenue Analytics',
                        style: Theme.of(context)
                            .textTheme
                            .headlineSmall
                            ?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: Theme.of(context).colorScheme.onPrimary,
                            ),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .onPrimary
                              .withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.flag,
                                size: 14,
                                color: Theme.of(context)
                                    .colorScheme
                                    .onPrimary
                                    .withValues(alpha: 0.9)),
                            const SizedBox(width: 6),
                            Text(
                              'From: Jan 16, 2026',
                              style: TextStyle(
                                  color: Theme.of(context)
                                      .colorScheme
                                      .onPrimary
                                      .withValues(alpha: 0.9),
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: _loadData,
                  icon: _isLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: LoadingIndicatorM3E(
                            constraints:
                                BoxConstraints(maxWidth: 16, maxHeight: 16),
                          ),
                        )
                      : const Icon(Icons.refresh, size: 18),
                  label: Text(_isLoading ? 'Loading...' : 'Refresh'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.onPrimary,
                    foregroundColor: Theme.of(context).colorScheme.primary,
                    elevation: 0,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 20, vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          if (_isLoading)
            const Center(child: LoadingIndicatorM3E())
          else ...[
            // Key Revenue Metrics
            Row(
              children: [
                Expanded(
                  child: _buildRevenueCard(
                    'Total Revenue',
                    '\$${totalRevenue.toStringAsFixed(2)}',
                    Icons.account_balance_wallet,
                    Theme.of(context).colorScheme.primary,
                    subtitle: 'All time earnings',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildRevenueCard(
                    'MRR',
                    '\$${mrr.toStringAsFixed(2)}',
                    Icons.trending_up,
                    Colors.blue,
                    subtitle: 'Monthly Recurring',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _buildRevenueCard(
                    'This Month',
                    '\$${thisMonthRevenue.toStringAsFixed(2)}',
                    Icons.calendar_today,
                    Colors.orange,
                    subtitle: '$newSubscribersThisMonth new subscribers',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildRevenueCard(
                    'Last Month',
                    '\$${lastMonthRevenue.toStringAsFixed(2)}',
                    Icons.history,
                    Colors.purple,
                    subtitle:
                        '${revenueGrowth >= 0 ? '+' : ''}${revenueGrowth.toStringAsFixed(1)}% growth',
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24),

            // Subscription Breakdown
            Text(
              '📊 Subscription Breakdown',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
            ),
            const SizedBox(height: 12),

            Container(
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                    color: Theme.of(context).colorScheme.outlineVariant),
              ),
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: [
                    _buildSubscriptionRow('Monthly Subscribers',
                        monthlySubscribers, Colors.blue, '\$2.98/mo'),
                    Divider(
                        color: Theme.of(context).colorScheme.outlineVariant),
                    _buildSubscriptionRow(
                        'Yearly Subscribers',
                        yearlySubscribers,
                        Theme.of(context).colorScheme.primary,
                        '\$19.00/yr'),
                    Divider(
                        color: Theme.of(context).colorScheme.outlineVariant),
                    _buildSubscriptionRow('Lifetime/One-time',
                        onetimeSubscribers, Colors.purple, '\$49.99'),
                    Divider(
                        color: Theme.of(context).colorScheme.outline,
                        thickness: 2),
                    _buildSubscriptionRow('Active Subscriptions',
                        activeSubscriptions, Colors.teal, ''),
                    Divider(
                        color: Theme.of(context).colorScheme.outlineVariant),
                    _buildSubscriptionRow(
                        'Expired', expiredSubscriptions, Colors.red, ''),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 24),

            // Conversion & Performance
            Text(
              '🎯 Performance Metrics',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
            ),
            const SizedBox(height: 12),

            Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    'Conversion Rate',
                    '${conversionRate.toStringAsFixed(1)}%',
                    Icons.pie_chart,
                    Theme.of(context).colorScheme.secondary,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildMetricCard(
                    'ARPU',
                    '\$${(activeSubscriptions > 0 ? totalRevenue / activeSubscriptions : 0).toStringAsFixed(2)}',
                    Icons.person_pin,
                    Colors.teal,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24),

            // Recent Transactions
            Text(
              '📝 Recent Transactions',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
            ),
            const SizedBox(height: 12),

            if (_recentTransactions.isEmpty)
              Card(
                color: Theme.of(context).colorScheme.surfaceContainerLowest,
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Center(
                    child: Text(
                      'No transactions yet',
                      style: TextStyle(
                          color:
                              Theme.of(context).colorScheme.onSurfaceVariant),
                    ),
                  ),
                ),
              )
            else
              Card(
                color: Theme.of(context).colorScheme.surfaceContainerLowest,
                child: ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _recentTransactions.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final tx = _recentTransactions[index];
                    final planType = tx['planType'] as String? ?? 'unknown';
                    final timestamp = tx['upgradedAt'] as Timestamp?;
                    final date = timestamp?.toDate();

                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor:
                            _getPlanColor(planType).withValues(alpha: 0.2),
                        child: Icon(
                          _getPlanIcon(planType),
                          color: _getPlanColor(planType),
                          size: 20,
                        ),
                      ),
                      title: Text(
                        tx['userName'] ?? 'Unknown',
                        style: TextStyle(
                            color: Theme.of(context).colorScheme.onSurface),
                      ),
                      subtitle: Text(
                        tx['email'] ?? '',
                        style: TextStyle(
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant),
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            _getPlanDisplayName(planType),
                            style: TextStyle(
                              color: _getPlanColor(planType),
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          if (date != null)
                            Text(
                              _formatDate(date),
                              style: TextStyle(
                                fontSize: 12,
                                color: Theme.of(context)
                                    .colorScheme
                                    .onSurfaceVariant,
                              ),
                            ),
                        ],
                      ),
                    );
                  },
                ),
              ),
          ],
        ],
      ),
    );
  }

  Widget _buildRevenueCard(
      String title, String value, IconData icon, Color color,
      {String? subtitle}) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: color, size: 22),
            ),
            const SizedBox(height: 16),
            Text(
              value,
              style: TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.bold,
                color: color,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              title,
              style: TextStyle(
                fontSize: 14,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  subtitle,
                  style: TextStyle(
                    fontSize: 11,
                    color: color,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSubscriptionRow(
      String label, int count, Color color, String price) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Center(
              child: Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: color,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                ),
                if (price.isNotEmpty)
                  Text(
                    price,
                    style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                        fontSize: 12),
                  ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              count.toString(),
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: color,
                fontSize: 16,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetricCard(
      String title, String value, IconData icon, Color color) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: color, size: 26),
            ),
            const SizedBox(height: 16),
            Text(
              value,
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              title,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Color _getPlanColor(String planType) {
    switch (planType.toLowerCase()) {
      case 'monthly':
        return Colors.blue;
      case 'yearly':
        return Colors.green;
      case 'onetime':
      case 'lifetime':
        return Colors.purple;
      default:
        return Colors.grey;
    }
  }

  IconData _getPlanIcon(String planType) {
    switch (planType.toLowerCase()) {
      case 'monthly':
        return Icons.calendar_month;
      case 'yearly':
        return Icons.calendar_today;
      case 'onetime':
      case 'lifetime':
        return Icons.all_inclusive;
      default:
        return Icons.help_outline;
    }
  }

  String _getPlanDisplayName(String planType) {
    switch (planType.toLowerCase()) {
      case 'monthly':
        return 'Monthly';
      case 'yearly':
        return 'Yearly';
      case 'onetime':
      case 'lifetime':
        return 'Lifetime';
      default:
        return planType;
    }
  }

  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final diff = now.difference(date);

    if (diff.inDays == 0) {
      return 'Today';
    } else if (diff.inDays == 1) {
      return 'Yesterday';
    } else if (diff.inDays < 7) {
      return '${diff.inDays} days ago';
    } else {
      return '${date.day}/${date.month}/${date.year}';
    }
  }

  Widget _buildApiUsageTab() {
    final totalApiCalls =
        (_apiUsageStats['totalApiCalls'] as num?)?.toInt() ?? 0;
    final thisMonthApiCalls =
        (_apiUsageStats['thisMonthApiCalls'] as num?)?.toInt() ?? 0;
    final thisMonthCreditsUsed =
        (_apiUsageStats['thisMonthCreditsUsed'] as num?)?.toInt() ?? 0;
    final activeNow = (_apiUsageStats['activeNow'] as num?)?.toInt() ?? 0;
    final dau = (_apiUsageStats['dau'] as num?)?.toInt() ?? 0;
    final trackingStartedAt = _apiUsageStats['trackingStartedAt'] as Timestamp?;
    final hasHistoricalData = _apiUsageStats['hasHistoricalData'] == true;

    final dailyCreditUsage =
        (_apiUsageStats['dailyCreditUsage'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>();
    final dailyApiUsage =
        (_apiUsageStats['dailyApiUsage'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>();
    final leaderboard = (_apiUsageStats['leaderboard'] as List<dynamic>? ?? [])
        .cast<Map<String, dynamic>>();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.api_rounded,
                      color: Theme.of(context).colorScheme.onPrimary,
                    ),
                    const SizedBox(width: 10),
                    Text(
                      'API Usage Analytics',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                            color: Theme.of(context).colorScheme.onPrimary,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  trackingStartedAt != null
                      ? 'Tracking since ${_formatDate(trackingStartedAt.toDate())}'
                      : 'Tracking data will appear once requests are recorded',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onPrimary
                            .withValues(alpha: 0.88),
                      ),
                ),
                if (!hasHistoricalData) ...[
                  const SizedBox(height: 8),
                  Text(
                    'Historical data may be partial before telemetry was enabled.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onPrimary
                              .withValues(alpha: 0.9),
                        ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  'Total API Calls',
                  totalApiCalls.toString(),
                  Icons.cloud_done,
                  Colors.blue,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildStatCard(
                  'This Month Calls',
                  thisMonthApiCalls.toString(),
                  Icons.calendar_month,
                  Colors.teal,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  'Credits This Month',
                  thisMonthCreditsUsed.toString(),
                  Icons.local_fire_department,
                  Colors.deepOrange,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildStatCard(
                  'Active (15m) / DAU',
                  '$activeNow / $dau',
                  Icons.groups,
                  Colors.purple,
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          _buildUsageTrendCard(
            title: 'Daily Credit Usage (This Month)',
            icon: Icons.stacked_line_chart,
            entries: dailyCreditUsage,
            valueKey: 'credits',
          ),
          const SizedBox(height: 16),
          _buildUsageTrendCard(
            title: 'Daily API Calls (This Month)',
            icon: Icons.timeline,
            entries: dailyApiUsage,
            valueKey: 'calls',
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Top Users Leaderboard',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 12),
                  if (leaderboard.isEmpty)
                    Text(
                      'No API usage data yet.',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    )
                  else
                    ...leaderboard.asMap().entries.map((entry) {
                      final index = entry.key;
                      final user = entry.value;
                      final name = user['name'] ?? 'Unknown User';
                      final email = user['email'] ?? '';
                      final calls =
                          (user['totalApiCalls'] as num?)?.toInt() ?? 0;
                      final credits =
                          (user['totalCreditsUsed'] as num?)?.toInt() ?? 0;
                      final successRate =
                          (user['successRate'] as num?)?.toDouble() ?? 0;

                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: CircleAvatar(
                          backgroundColor:
                              Theme.of(context).colorScheme.secondaryContainer,
                          child: Text(
                            '${index + 1}',
                            style: TextStyle(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSecondaryContainer,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        title: Text(name.toString()),
                        subtitle: Text(
                          '$email\n$calls calls • $credits credits • ${successRate.toStringAsFixed(1)}% success',
                        ),
                        isThreeLine: true,
                      );
                    }),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUsageTrendCard({
    required String title,
    required IconData icon,
    required List<Map<String, dynamic>> entries,
    required String valueKey,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (entries.isEmpty)
              Text(
                'No data available yet.',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              )
            else
              ...entries.reversed.take(14).toList().reversed.map((item) {
                final date = item['date']?.toString() ?? '-';
                final value = (item[valueKey] as num?)?.toInt() ?? 0;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 92,
                        child: Text(
                          date.length >= 10 ? date.substring(5, 10) : date,
                          style: TextStyle(
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      Expanded(
                        child: LinearProgressIndicator(
                          value: (value /
                                  ((entries
                                      .map((e) =>
                                          (e[valueKey] as num?)?.toInt() ?? 0)
                                      .fold<int>(1, (a, b) => a > b ? a : b))))
                              .clamp(0.0, 1.0),
                          minHeight: 8,
                          borderRadius: BorderRadius.circular(6),
                          backgroundColor: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        value.toString(),
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  Widget _buildNotificationsTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Send Push Notifications',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 16),
          Card(
            elevation: 2,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: _titleController,
                    decoration: const InputDecoration(
                      labelText: 'Notification Title',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.title),
                    ),
                    maxLength: 100,
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _bodyController,
                    decoration: const InputDecoration(
                      labelText: 'Notification Message',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.message),
                    ),
                    maxLines: 4,
                    maxLength: 500,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    'Send to:',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 16,
                    runSpacing: 16,
                    children: [
                      _buildSendButton(
                        'All Users',
                        Icons.people,
                        Colors.blue,
                        () => _sendNotification('all'),
                      ),
                      _buildSendButton(
                        'Pro Users',
                        Icons.star,
                        Colors.orange,
                        () => _sendNotification('pro'),
                      ),
                      _buildSendButton(
                        'Free Users',
                        Icons.person,
                        Colors.green,
                        () => _sendNotification('free'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  _buildQuickTemplates(),
                ],
              ),
            ),
          ),
          if (_statusMessage != null) ...[
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: _statusIsError
                    ? Theme.of(context).colorScheme.errorContainer
                    : Theme.of(context).colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    _statusIsError
                        ? Icons.error_outline
                        : Icons.check_circle_outline,
                    color: _statusIsError
                        ? Theme.of(context).colorScheme.error
                        : Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _statusMessage!,
                      style: TextStyle(
                        color: _statusIsError
                            ? Theme.of(context).colorScheme.onErrorContainer
                            : Theme.of(context).colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildSendButton(
    String label,
    IconData icon,
    Color color,
    VoidCallback onPressed,
  ) {
    return SizedBox(
      width: 120,
      child: ElevatedButton.icon(
        onPressed: _isLoading ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: Theme.of(context).colorScheme.onPrimary,
          padding: const EdgeInsets.symmetric(vertical: 12),
        ),
        icon: _isLoading
            ? const SizedBox(
                width: 16,
                height: 16,
                child: LoadingIndicatorM3E(
                  constraints: BoxConstraints(maxWidth: 16, maxHeight: 16),
                ),
              )
            : Icon(icon, size: 18),
        label: Text(label, style: const TextStyle(fontSize: 12)),
      ),
    );
  }

  Widget _buildQuickTemplates() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Quick Templates',
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            ActionChip(
              label: const Text('App Update'),
              avatar: const Icon(Icons.update, size: 18),
              onPressed: () {
                _titleController.text = 'New Update Available!';
                _bodyController.text =
                    'A new version of Rewordium is available. Update now to get the latest features and improvements!';
              },
            ),
            ActionChip(
              label: const Text('Maintenance'),
              avatar: const Icon(Icons.build, size: 18),
              onPressed: () {
                _titleController.text = 'Scheduled Maintenance';
                _bodyController.text =
                    'We\'ll be performing scheduled maintenance. The app may be temporarily unavailable during this time.';
              },
            ),
            ActionChip(
              label: const Text('New Features'),
              avatar: const Icon(Icons.new_releases, size: 18),
              onPressed: () {
                _titleController.text = 'New Features Available!';
                _bodyController.text =
                    'We\'ve added exciting new AI features to enhance your writing experience. Check them out now!';
              },
            ),
            ActionChip(
              label: const Text('Pro Offer'),
              avatar: const Icon(Icons.local_offer, size: 18),
              onPressed: () {
                _titleController.text = 'Special Pro Offer!';
                _bodyController.text =
                    'Upgrade to Pro now and get 50% off your first month. Unlock unlimited AI assistance!';
              },
            ),
          ],
        ),
      ],
    );
  }

  // Apply current filter and sort to users
  void _applyFilterAndSort() async {
    setState(() => _isLoading = true);

    try {
      List<UserModel> result;

      if (_searchController.text.isNotEmpty) {
        // If searching, use search results
        result = await AdminService.searchUsers(_searchController.text);
      } else {
        // Otherwise use filter
        result = await AdminService.getFilteredUsers(
          filterBy: _filterBy,
          sortBy: _sortBy,
          ascending: _sortAscending,
        );
      }

      setState(() {
        _filteredUsers = result;
      });
    } catch (e) {
      _showStatus('Error filtering users: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  // Export users to CSV
  Future<void> _exportUsersToCsv() async {
    setState(() => _isLoading = true);

    try {
      // Use currently filtered users
      final usersToExport = _filteredUsers.isNotEmpty ? _filteredUsers : _users;

      if (usersToExport.isEmpty) {
        _showStatus('No users to export', isError: true);
        return;
      }

      final csvData = AdminService.generateUsersCsv(usersToExport);

      // Get temp directory
      final directory = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final filterSuffix = _filterBy == 'all' ? '' : '_$_filterBy';
      final file = File(
          '${directory.path}/rewordium_users${filterSuffix}_$timestamp.csv');

      await file.writeAsString(csvData);

      // Share the file
      await Share.shareXFiles(
        [XFile(file.path)],
        subject: 'Rewordium Users Export',
        text: 'Exported ${usersToExport.length} users (Filter: $_filterBy)',
      );

      _showStatus('Exported ${usersToExport.length} users successfully!');
    } catch (e) {
      _showStatus('Error exporting users: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Widget _buildUsersTab() {
    return Column(
      children: [
        // Search and Filter Controls
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Search bar
              TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  labelText: 'Search users by name or email',
                  border: const OutlineInputBorder(),
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _searchController.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            _searchController.clear();
                            _applyFilterAndSort();
                          },
                        )
                      : null,
                ),
                onChanged: _filterUsers,
              ),
              const SizedBox(height: 16),

              // Filter and Sort Row
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    // Filter dropdown
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      decoration: BoxDecoration(
                        border: Border.all(color: Colors.grey.shade400),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _filterBy,
                          icon: const Icon(Icons.filter_list),
                          hint: const Text('Filter'),
                          items: const [
                            DropdownMenuItem(
                                value: 'all', child: Text('All Users')),
                            DropdownMenuItem(
                                value: 'pro', child: Text('Pro Users')),
                            DropdownMenuItem(
                                value: 'free', child: Text('Free Users')),
                            DropdownMenuItem(
                                value: 'news_subscribers',
                                child: Text('📧 News Subscribers')),
                          ],
                          onChanged: (value) {
                            setState(() => _filterBy = value ?? 'all');
                            _applyFilterAndSort();
                          },
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),

                    // Sort dropdown
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      decoration: BoxDecoration(
                        border: Border.all(color: Colors.grey.shade400),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _sortBy,
                          icon: const Icon(Icons.sort),
                          hint: const Text('Sort By'),
                          items: const [
                            DropdownMenuItem(
                                value: 'createdAt', child: Text('Date Joined')),
                            DropdownMenuItem(
                                value: 'name', child: Text('Name')),
                            DropdownMenuItem(
                                value: 'email', child: Text('Email')),
                            DropdownMenuItem(
                                value: 'credits', child: Text('Credits')),
                          ],
                          onChanged: (value) {
                            setState(() => _sortBy = value ?? 'createdAt');
                            _applyFilterAndSort();
                          },
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),

                    // Sort direction toggle
                    IconButton(
                      icon: Icon(
                        _sortAscending
                            ? Icons.arrow_upward
                            : Icons.arrow_downward,
                        color: Colors.blue,
                      ),
                      tooltip: _sortAscending ? 'Ascending' : 'Descending',
                      onPressed: () {
                        setState(() => _sortAscending = !_sortAscending);
                        _applyFilterAndSort();
                      },
                    ),
                    const SizedBox(width: 12),

                    // Export button
                    ElevatedButton.icon(
                      onPressed: _isLoading ? null : _exportUsersToCsv,
                      icon: _isLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: LoadingIndicatorM3E(
                                constraints:
                                    BoxConstraints(maxWidth: 16, maxHeight: 16),
                              ),
                            )
                          : const Icon(Icons.download, size: 18),
                      label: const Text('Export CSV'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        foregroundColor: Colors.white,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Results summary
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.blue.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.people, size: 16, color: Colors.blue),
                    const SizedBox(width: 8),
                    Text(
                      'Showing ${_filteredUsers.length} users',
                      style: const TextStyle(
                        color: Colors.blue,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    if (_filterBy != 'all') ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: Colors.blue,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _filterBy == 'news_subscribers'
                              ? '📧 News'
                              : _filterBy.toUpperCase(),
                          style: TextStyle(
                              color: Theme.of(context).colorScheme.onPrimary,
                              fontSize: 11),
                        ),
                      ),
                    ],
                  ],
                ),
              ),

              if (_users.isEmpty && !_isLoading) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.orange.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                    border:
                        Border.all(color: Colors.orange.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline, color: Colors.orange),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'No users found in database',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.orange.shade800,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Click "Refresh Data" button to reload user information',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.orange.shade700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
        Expanded(
          child: _isLoading
              ? const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      LoadingIndicatorM3E(),
                      SizedBox(height: 16),
                      Text('Loading users...'),
                    ],
                  ),
                )
              : _filteredUsers.isEmpty
                  ? const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.people_outline,
                              size: 64, color: Colors.grey),
                          SizedBox(height: 16),
                          Text('No users match your search'),
                        ],
                      ),
                    )
                  : ListView.builder(
                      itemCount: _filteredUsers.length,
                      itemBuilder: (context, index) {
                        final user = _filteredUsers[index];
                        return _buildUserCard(user);
                      },
                    ),
        ),
      ],
    );
  }

  Widget _buildUserCard(UserModel user) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ExpansionTile(
        leading: Stack(
          children: [
            CircleAvatar(
              backgroundColor: user.isPro ? Colors.orange : Colors.grey,
              child: Icon(
                user.isPro ? Icons.star : Icons.person,
                color: Colors.white,
              ),
            ),
            if (user.subscribedToNews)
              Positioned(
                right: -2,
                bottom: -2,
                child: Container(
                  padding: const EdgeInsets.all(2),
                  decoration: BoxDecoration(
                    color: Colors.green,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 1.5),
                  ),
                  child: const Icon(Icons.mail, size: 10, color: Colors.white),
                ),
              ),
          ],
        ),
        title: Text(user.name),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(user.email),
            Row(
              children: [
                Text('${user.userType} • ${user.credits} credits'),
                if (user.subscribedToNews) ...[
                  const SizedBox(width: 8),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.green.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text(
                      '📧 News',
                      style: TextStyle(fontSize: 10, color: Colors.green),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('UID: ${user.uid}',
                              style: const TextStyle(fontSize: 12)),
                          Text(
                              'Created: ${user.createdAt.toLocal().toString().split(' ')[0]}'),
                          Text('Sign-in: ${user.signInMethod}'),
                          Text('Status: ${user.status}'),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  children: [
                    ElevatedButton.icon(
                      onPressed: () => _sendIndividualNotification(user),
                      icon: const Icon(Icons.send, size: 16),
                      label: const Text('Send Message'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor:
                            Theme.of(context).colorScheme.onPrimary,
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: () => _toggleProStatus(user),
                      icon: Icon(
                        user.isPro ? Icons.star_border : Icons.star,
                        size: 16,
                      ),
                      label: Text(user.isPro ? 'Remove Pro' : 'Make Pro'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor:
                            user.isPro ? Colors.grey : Colors.orange,
                        foregroundColor:
                            Theme.of(context).colorScheme.onPrimary,
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: () => _updateCredits(user),
                      icon: const Icon(Icons.add_circle, size: 16),
                      label: const Text('Add Credits'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        foregroundColor:
                            Theme.of(context).colorScheme.onPrimary,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHistoryTab() {
    return FutureBuilder<List<Map<String, dynamic>>>(
      future: AdminService.getNotificationHistory(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: LoadingIndicatorM3E());
        }

        if (!snapshot.hasData || snapshot.data!.isEmpty) {
          return const Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.history, size: 64, color: Colors.grey),
                SizedBox(height: 16),
                Text('No notification history found'),
              ],
            ),
          );
        }

        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: snapshot.data!.length,
          itemBuilder: (context, index) {
            final notification = snapshot.data![index];
            return _buildHistoryCard(notification);
          },
        );
      },
    );
  }

  Widget _buildHistoryCard(Map<String, dynamic> notification) {
    final sentAt = notification['sentAt']?.toDate() ?? DateTime.now();
    final target = notification['target'] ?? 'unknown';

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: _getTargetColor(target),
          child: Icon(_getTargetIcon(target), color: Colors.white),
        ),
        title: Text(notification['title'] ?? 'No Title'),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(notification['body'] ?? 'No Message'),
            const SizedBox(height: 4),
            Text(
              'Sent to: $target • ${sentAt.toLocal().toString().split('.')[0]}',
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
        isThreeLine: true,
      ),
    );
  }

  Color _getTargetColor(String target) {
    switch (target) {
      case 'all_users':
        return Colors.blue;
      case 'pro_users':
        return Colors.orange;
      case 'free_users':
        return Colors.green;
      default:
        return Colors.grey;
    }
  }

  IconData _getTargetIcon(String target) {
    switch (target) {
      case 'all_users':
        return Icons.people;
      case 'pro_users':
        return Icons.star;
      case 'free_users':
        return Icons.person;
      default:
        return Icons.send;
    }
  }

  void _sendIndividualNotification(UserModel user) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Send message to ${user.name}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _titleController,
              decoration: const InputDecoration(
                labelText: 'Title',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _bodyController,
              decoration: const InputDecoration(
                labelText: 'Message',
                border: OutlineInputBorder(),
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
              Navigator.pop(context);
              _sendNotification('individual', userId: user.uid);
            },
            child: const Text('Send'),
          ),
        ],
      ),
    );
  }

  void _toggleProStatus(UserModel user) async {
    final success =
        await AdminService.updateUserProStatus(user.uid, !user.isPro);
    if (success) {
      _showStatus('User status updated successfully!');
      _loadData();
    } else {
      _showStatus('Failed to update user status', isError: true);
    }
  }

  void _updateCredits(UserModel user) {
    final creditController =
        TextEditingController(text: user.credits.toString());

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Update credits for ${user.name}'),
        content: TextField(
          controller: creditController,
          decoration: const InputDecoration(
            labelText: 'Credits',
            border: OutlineInputBorder(),
          ),
          keyboardType: TextInputType.number,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              final credits = int.tryParse(creditController.text) ?? 0;
              Navigator.pop(context);

              final success =
                  await AdminService.updateUserCredits(user.uid, credits);
              if (success) {
                _showStatus('Credits updated successfully!');
                _loadData();
              } else {
                _showStatus('Failed to update credits', isError: true);
              }
            },
            child: const Text('Update'),
          ),
        ],
      ),
    );
  }

  Future<void> _searchUsers(String query) async {
    if (query.isEmpty) {
      setState(() => _filteredUsers = _users);
      return;
    }

    setState(() => _isLoading = true);

    try {
      final results = await AdminService.searchUsers(query);
      setState(() => _filteredUsers = results);
    } catch (e) {
      _showStatus('Error searching users: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }
}
