import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import 'session.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.session});

  final SessionController session;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _username = TextEditingController();
  final _pin = TextEditingController();
  bool _busy = false;
  String? _error;

  Future<void> _submit() async {
    if (_username.text.trim().isEmpty || _pin.text.isEmpty) {
      setState(() => _error = 'Enter username and PIN');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.session.login(_username.text.trim(), _pin.text);
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _pin.clear();
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    _username.dispose();
    _pin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final notice = widget.session.notice;
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: AutofillGroup(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Paynash POS',
                      style: Theme.of(context).textTheme.headlineMedium,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 24),
                  if (notice != null && _error == null) ...[
                    Text(notice, textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                  ],
                  TextField(
                    key: const Key('login-username'),
                    controller: _username,
                    decoration: const InputDecoration(labelText: 'Username'),
                    autofillHints: const [AutofillHints.username],
                    textInputAction: TextInputAction.next,
                    enabled: !_busy,
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    key: const Key('login-pin'),
                    controller: _pin,
                    decoration: const InputDecoration(labelText: 'PIN'),
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                      LengthLimitingTextInputFormatter(8),
                    ],
                    onSubmitted: (_) => _submit(),
                    enabled: !_busy,
                  ),
                  const SizedBox(height: 12),
                  if (_error != null)
                    Text(_error!,
                        style: TextStyle(color: Theme.of(context).colorScheme.error),
                        textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  FilledButton(
                    key: const Key('login-submit'),
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Log in'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
