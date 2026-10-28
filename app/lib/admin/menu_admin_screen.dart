import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import '../menu/menu_models.dart';
import 'menu_admin_api.dart';

/// Admin: categories on the left, the selected category's items on the right.
class MenuAdminScreen extends StatefulWidget {
  const MenuAdminScreen({super.key, required this.api, this.pickImage = pickImageFromDevice});

  final MenuAdminApi api;
  final ImagePicker pickImage;

  @override
  State<MenuAdminScreen> createState() => _MenuAdminScreenState();
}

class _MenuAdminScreenState extends State<MenuAdminScreen> {
  List<AdminCategory>? _categories;
  List<AdminItem> _items = [];
  int? _selectedId;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([widget.api.categories(), widget.api.items()]);
      if (!mounted) return;
      setState(() {
        _categories = results[0] as List<AdminCategory>;
        _items = results[1] as List<AdminItem>;
        _error = null;
        if (!_categories!.any((c) => c.id == _selectedId)) {
          _selectedId = _categories!.isEmpty ? null : _categories!.first.id;
        }
      });
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _showError(Object e) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
  }

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } on ApiError catch (e) {
      if (mounted) _showError(e);
    }
    await _load();
  }

  Future<void> _editCategory([AdminCategory? category]) async {
    final name = await showDialog<String>(
      context: context,
      builder: (_) => _NameDialog(
          title: category == null ? 'Add category' : 'Rename category',
          initial: category?.name),
    );
    if (name == null || name.trim().isEmpty) return;
    await _run(() => category == null
        ? widget.api.createCategory(name.trim())
        : widget.api.renameCategory(category.id, name.trim()));
  }

  Future<void> _editItem([AdminItem? item]) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => ItemDialog(
        api: widget.api,
        categories: _categories!,
        categoryId: item?.categoryId ?? _selectedId!,
        item: item,
        pickImage: widget.pickImage,
      ),
    );
    if (saved == true) await _load();
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) return Center(child: Text(_error!));
    final categories = _categories;
    if (categories == null) return const Center(child: CircularProgressIndicator());

    final items = _items.where((i) => i.categoryId == _selectedId).toList();
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          width: 280,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ListTile(
                title: Text('Categories', style: Theme.of(context).textTheme.titleMedium),
                trailing: IconButton(
                  key: const Key('category-add'),
                  tooltip: 'Add category',
                  icon: const Icon(Icons.add),
                  onPressed: () => _editCategory(),
                ),
              ),
              Expanded(
                child: ReorderableListView(
                  buildDefaultDragHandles: false,
                  onReorderItem: (from, to) {
                    final ids = [for (final c in categories) c.id];
                    final moved = ids.removeAt(from);
                    ids.insert(to, moved);
                    _run(() => widget.api.reorderCategories(ids));
                  },
                  children: [
                    for (final (i, c) in categories.indexed)
                      ListTile(
                        key: Key('category-${c.id}'),
                        selected: c.id == _selectedId,
                        leading: ReorderableDragStartListener(
                            index: i, child: const Icon(Icons.drag_indicator)),
                        title: Text(c.name),
                        onTap: () => setState(() => _selectedId = c.id),
                        trailing: PopupMenuButton<String>(
                          onSelected: (action) => action == 'rename'
                              ? _editCategory(c)
                              : _run(() => widget.api.deleteCategory(c.id)),
                          itemBuilder: (_) => const [
                            PopupMenuItem(value: 'rename', child: Text('Rename')),
                            PopupMenuItem(value: 'delete', child: Text('Delete')),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const VerticalDivider(width: 1),
        Expanded(
          child: _selectedId == null
              ? const Center(child: Text('Add a category to start.'))
              : Scaffold(
                  floatingActionButton: FloatingActionButton.extended(
                    key: const Key('item-add'),
                    onPressed: () => _editItem(),
                    icon: const Icon(Icons.add),
                    label: const Text('Add item'),
                  ),
                  body: items.isEmpty
                      ? const Center(child: Text('No items in this category yet.'))
                      : ReorderableListView(
                          buildDefaultDragHandles: false,
                          padding: const EdgeInsets.only(bottom: 88),
                          onReorderItem: (from, to) {
                            final ids = [for (final i in items) i.id];
                            final moved = ids.removeAt(from);
                            ids.insert(to, moved);
                            _run(() => widget.api.reorderItems(_selectedId!, ids));
                          },
                          children: [
                            for (final (index, item) in items.indexed)
                              ListTile(
                                key: Key('item-${item.id}'),
                                leading: Row(mainAxisSize: MainAxisSize.min, children: [
                                  ReorderableDragStartListener(
                                      index: index, child: const Icon(Icons.drag_indicator)),
                                  const SizedBox(width: 8),
                                  _Thumbnail(item: item, url: widget.api.imageUrl),
                                ]),
                                title: Text(item.name,
                                    style: item.active
                                        ? null
                                        : const TextStyle(decoration: TextDecoration.lineThrough)),
                                subtitle: Text(
                                    'CHF ${item.price}${item.active ? '' : ' · inactive'}'),
                                trailing: ItemTypeBadge(item.type),
                                onTap: () => _editItem(item),
                              ),
                          ],
                        ),
                ),
        ),
      ],
    );
  }
}

class _Thumbnail extends StatelessWidget {
  const _Thumbnail({required this.item, required this.url});

  final AdminItem item;
  final String Function(String) url;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(6),
      child: SizedBox.square(
        dimension: 48,
        child: item.imageUrl == null
            ? ColoredBox(
                color: item.type.color.withValues(alpha: 0.12),
                child: Icon(item.type.icon, color: item.type.color))
            : Image.network(url(item.imageUrl!),
                key: Key('thumb-${item.id}'),
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const Icon(Icons.broken_image)),
      ),
    );
  }
}

class _NameDialog extends StatefulWidget {
  const _NameDialog({required this.title, this.initial});

  final String title;
  final String? initial;

  @override
  State<_NameDialog> createState() => _NameDialogState();
}

class _NameDialogState extends State<_NameDialog> {
  late final _name = TextEditingController(text: widget.initial);

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        key: const Key('category-name'),
        controller: _name,
        autofocus: true,
        decoration: const InputDecoration(labelText: 'Name'),
        onSubmitted: (v) => Navigator.pop(context, v),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
            key: const Key('category-save'),
            onPressed: () => Navigator.pop(context, _name.text),
            child: const Text('Save')),
      ],
    );
  }
}

/// Create or edit an item, including its image.
class ItemDialog extends StatefulWidget {
  const ItemDialog({
    super.key,
    required this.api,
    required this.categories,
    required this.categoryId,
    required this.pickImage,
    this.item,
  });

  final MenuAdminApi api;
  final List<AdminCategory> categories;
  final int categoryId;
  final AdminItem? item;
  final ImagePicker pickImage;

  @override
  State<ItemDialog> createState() => _ItemDialogState();
}

class _ItemDialogState extends State<ItemDialog> {
  late final _name = TextEditingController(text: widget.item?.name);
  late final _price = TextEditingController(text: widget.item?.price);
  late final _description = TextEditingController(text: widget.item?.description);
  late int _categoryId = widget.categoryId;
  late ItemType? _type = widget.item?.type;
  late bool _active = widget.item?.active ?? true;

  /// The saved item, set after the first successful save so a failed image
  /// upload can be retried without creating the item twice.
  late AdminItem? _saved = widget.item;
  PickedImage? _newImage;
  bool _removeImage = false;

  Map<String, String> _fieldErrors = {};
  String? _error;
  bool _busy = false;

  Future<void> _chooseImage() async {
    final picked = await widget.pickImage();
    if (picked != null) {
      setState(() {
        _newImage = picked;
        _removeImage = false;
        _fieldErrors = {..._fieldErrors}..remove('file');
      });
    }
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
      _fieldErrors = {};
    });
    final input = ItemInput(
      categoryId: _categoryId,
      name: _name.text.trim(),
      type: _type,
      price: _price.text.trim(),
      description: _description.text.trim(),
      active: _active,
    );
    try {
      _saved = _saved == null
          ? await widget.api.createItem(input)
          : await widget.api.updateItem(_saved!.id, input);
      if (_newImage != null) {
        await widget.api.uploadImage(_saved!.id, _newImage!);
      } else if (_removeImage && _saved!.imageUrl != null) {
        await widget.api.removeImage(_saved!.id);
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiError catch (e) {
      if (mounted) {
        setState(() {
          _fieldErrors = e.fieldErrors;
          _error = e.fieldErrors.isEmpty ? e.message : null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _preview() {
    final Widget image;
    if (_newImage != null) {
      image = Image.memory(Uint8List.fromList(_newImage!.bytes),
          key: const Key('image-preview'), fit: BoxFit.cover);
    } else if (!_removeImage && _saved?.imageUrl != null) {
      image = Image.network(widget.api.imageUrl(_saved!.imageUrl!),
          key: const Key('image-preview'),
          fit: BoxFit.cover,
          errorBuilder: (_, _, _) => const Icon(Icons.broken_image));
    } else {
      final type = _type ?? ItemType.food;
      image = ColoredBox(
        key: const Key('image-placeholder'),
        color: type.color.withValues(alpha: 0.12),
        child: Icon(_type?.icon ?? Icons.image_outlined, size: 40, color: type.color),
      );
    }
    final hasImage = _newImage != null || (!_removeImage && _saved?.imageUrl != null);
    return Row(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: SizedBox.square(dimension: 96, child: image),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              OutlinedButton.icon(
                key: const Key('image-choose'),
                onPressed: _busy ? null : _chooseImage,
                icon: const Icon(Icons.upload),
                label: Text(hasImage ? 'Replace image' : 'Choose image'),
              ),
              if (hasImage)
                TextButton(
                  key: const Key('image-remove'),
                  onPressed: _busy
                      ? null
                      : () => setState(() {
                            _newImage = null;
                            _removeImage = true;
                          }),
                  child: const Text('Remove image'),
                ),
              Text('JPEG, PNG or WebP, max. 5 MB',
                  style: Theme.of(context).textTheme.bodySmall),
              if (_fieldErrors['file'] != null)
                Text(_fieldErrors['file']!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final error = Theme.of(context).colorScheme.error;
    return AlertDialog(
      title: Text(widget.item == null ? 'Add item' : 'Edit ${widget.item!.name}'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _preview(),
              const SizedBox(height: 12),
              TextField(
                key: const Key('item-name'),
                controller: _name,
                maxLength: 80,
                decoration:
                    InputDecoration(labelText: 'Name', errorText: _fieldErrors['name']),
              ),
              Text('Type', style: Theme.of(context).textTheme.labelLarge),
              const SizedBox(height: 4),
              SegmentedButton<ItemType>(
                key: const Key('item-type'),
                emptySelectionAllowed: true,
                segments: [
                  for (final t in ItemType.values)
                    ButtonSegment(value: t, label: Text(t.label), icon: Icon(t.icon)),
                ],
                selected: {?_type},
                onSelectionChanged: (s) =>
                    setState(() => _type = s.isEmpty ? _type : s.first),
              ),
              if (_fieldErrors['type'] != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(_fieldErrors['type']!, style: TextStyle(color: error)),
                ),
              TextField(
                key: const Key('item-price'),
                controller: _price,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'^\d{0,5}(\.\d{0,2})?')),
                ],
                decoration: InputDecoration(
                    labelText: 'Price', prefixText: 'CHF ', errorText: _fieldErrors['price']),
              ),
              DropdownButtonFormField<int>(
                key: const Key('item-category'),
                initialValue: _categoryId,
                decoration: InputDecoration(
                    labelText: 'Category', errorText: _fieldErrors['categoryId']),
                items: [
                  for (final c in widget.categories)
                    DropdownMenuItem(value: c.id, child: Text(c.name)),
                ],
                onChanged: (v) => setState(() => _categoryId = v ?? _categoryId),
              ),
              TextField(
                key: const Key('item-description'),
                controller: _description,
                maxLength: 200,
                decoration: InputDecoration(
                    labelText: 'Short description (optional)',
                    errorText: _fieldErrors['description']),
              ),
              SwitchListTile(
                key: const Key('item-active'),
                contentPadding: EdgeInsets.zero,
                title: const Text('Active'),
                subtitle: const Text('Inactive items are not offered to waiters'),
                value: _active,
                onChanged: (v) => setState(() => _active = v),
              ),
              if (_error != null) Text(_error!, style: TextStyle(color: error)),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context, _saved != widget.item),
            child: const Text('Cancel')),
        FilledButton(
            key: const Key('item-save'),
            onPressed: _busy ? null : _save,
            child: const Text('Save')),
      ],
    );
  }
}
