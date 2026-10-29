import 'package:flutter/material.dart';

/// Asks for a note or a reason (at most 140 characters). Returns the text, or
/// null when dismissed.
class TextDialog extends StatefulWidget {
  const TextDialog({
    super.key,
    required this.title,
    required this.label,
    required this.action,
    this.initial,
    this.required = false,
  });

  final String title;
  final String label;
  final String action;
  final String? initial;
  final bool required;

  @override
  State<TextDialog> createState() => _TextDialogState();
}

class _TextDialogState extends State<TextDialog> {
  late final _text = TextEditingController(text: widget.initial);
  String? _error;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  void _submit() {
    final value = _text.text.trim();
    if (widget.required && value.isEmpty) {
      setState(() => _error = '${widget.label} is required');
      return;
    }
    Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        key: const Key('dialog-text'),
        controller: _text,
        autofocus: true,
        maxLength: 140,
        decoration: InputDecoration(labelText: widget.label, errorText: _error),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Back')),
        FilledButton(
          key: const Key('dialog-submit'),
          onPressed: _submit,
          child: Text(widget.action),
        ),
      ],
    );
  }
}
