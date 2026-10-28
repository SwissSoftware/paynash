import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

/// Refreshes a screen every [pollInterval] while it is visible: its route is
/// on top and the app is in the foreground (design D4: 3 s polling instead of
/// push, which keeps other devices in sync within 5 s).
mixin Polling<T extends StatefulWidget> on State<T> {
  static const pollInterval = Duration(seconds: 3);

  Timer? _timer;
  AppLifecycleListener? _lifecycle;
  bool _polling = false;

  /// Loads fresh data. Implementations handle their own errors.
  Future<void> poll();

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(onResume: _tick);
    _timer = Timer.periodic(pollInterval, (_) => _tick());
  }

  bool get _visible {
    final route = ModalRoute.of(context);
    final app = SchedulerBinding.instance.lifecycleState;
    return (route == null || route.isCurrent) && (app == null || app == AppLifecycleState.resumed);
  }

  Future<void> _tick() async {
    if (!mounted || _polling || !_visible) return;
    _polling = true;
    try {
      await poll();
    } finally {
      _polling = false;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _lifecycle?.dispose();
    super.dispose();
  }
}

/// Shown while the backend cannot be reached. Sending is blocked meanwhile
/// (design: no offline order taking).
class NoConnectionBanner extends StatelessWidget {
  const NoConnectionBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Material(
      key: const Key('no-connection'),
      color: colors.errorContainer,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          children: [
            Icon(Icons.wifi_off, color: colors.onErrorContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'No connection to the server. Orders cannot be sent until it is back.',
                style: TextStyle(color: colors.onErrorContainer),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
