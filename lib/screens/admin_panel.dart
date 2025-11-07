import 'package:flutter/material.dart';
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
  Map<String, int> _userStats = {'total': 0, 'pro': 0, 'free': 0};

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
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

      setState(() {
        _users = users;
        _filteredUsers = users;
        _userStats = stats;
      });
    } catch (e) {
      _showStatus('Error loading data: $e', isError: true);
    } finally {
      setState(() => _isLoading = false);
    }
  }

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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Dashboard'),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadData,
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              setState(() {
                _isAuthenticated = false;
                _passwordController.clear();
              });
            },
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(icon: Icon(Icons.dashboard), text: 'Overview'),
            Tab(icon: Icon(Icons.send), text: 'Notifications'),
            Tab(icon: Icon(Icons.people), text: 'Users'),
            Tab(icon: Icon(Icons.history), text: 'History'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildOverviewTab(),
          _buildNotificationsTab(),
          _buildUsersTab(),
          _buildHistoryTab(),
        ],
      ),
    );
  }

  Widget _buildOverviewTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'User Statistics',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              ElevatedButton.icon(
                onPressed: _loadData,
                icon: _isLoading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh, size: 16),
                label: Text(_isLoading ? 'Refreshing...' : 'Refresh Data'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (_isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  children: [
                    CircularProgressIndicator(),
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
                    Colors.green,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildStatCard(
                    'Active Now',
                    '${(_userStats['total']! * 0.3).round()}',
                    Icons.radio_button_checked,
                    Colors.red,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 32),
            Text(
              'Quick Actions',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                _buildQuickActionCard(
                  'Broadcast to All',
                  Icons.campaign,
                  Colors.purple,
                  () => _tabController.animateTo(1),
                ),
                _buildQuickActionCard(
                  'Manage Users',
                  Icons.manage_accounts,
                  Colors.teal,
                  () => _tabController.animateTo(2),
                ),
                _buildQuickActionCard(
                  'View History',
                  Icons.history,
                  Colors.indigo,
                  () => _tabController.animateTo(3),
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
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(icon, color: color, size: 32),
                Text(
                  value,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: color,
                      ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              title,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w500,
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
    return SizedBox(
      width: 150,
      child: Card(
        elevation: 2,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Icon(icon, color: color, size: 48),
                const SizedBox(height: 12),
                Text(
                  title,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                ),
              ],
            ),
          ),
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
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(vertical: 12),
        ),
        icon: _isLoading
            ? const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
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

  Widget _buildUsersTab() {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              TextField(
                controller: _searchController,
                decoration: const InputDecoration(
                  labelText: 'Search users by name or email',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.search),
                ),
                onChanged: _filterUsers,
              ),
              const SizedBox(height: 16),
              if (_users.isEmpty && !_isLoading)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.orange.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.orange.withOpacity(0.3)),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.orange),
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
          ),
        ),
        Expanded(
          child: _isLoading
              ? const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
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
        leading: CircleAvatar(
          backgroundColor: user.isPro ? Colors.orange : Colors.grey,
          child: Icon(
            user.isPro ? Icons.star : Icons.person,
            color: Colors.white,
          ),
        ),
        title: Text(user.name),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(user.email),
            Text('${user.userType} • ${user.credits} credits'),
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
                        foregroundColor: Colors.white,
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
                        foregroundColor: Colors.white,
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: () => _updateCredits(user),
                      icon: const Icon(Icons.add_circle, size: 16),
                      label: const Text('Add Credits'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        foregroundColor: Colors.white,
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
          return const Center(child: CircularProgressIndicator());
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
