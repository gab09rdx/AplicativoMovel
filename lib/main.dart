import 'package:flutter/material.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

void main() {
  runApp(const MaterialApp(
    home: PlanetManagerApp(),
    debugShowCheckedModeBanner: false,
  ));
}

class Planet {
  int? id;
  final String name;
  final double distance;
  final double size;
  final String? nickname;

  Planet({
    this.id,
    required this.name,
    required this.distance,
    required this.size,
    this.nickname,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'distance': distance,
      'size': size,
      'nickname': nickname,
    };
  }
}

class DatabaseService {
  static final DatabaseService instance = DatabaseService._initialize();
  static Database? _db;

  DatabaseService._initialize();

  Future<Database> get database async {
    if (_db != null) return _db!;
    _db = await _setupDatabase('planets.db');
    return _db!;
  }

  Future<Database> _setupDatabase(String fileName) async {
    final dbDirectory = await getDatabasesPath();
    final fullPath = join(dbDirectory, fileName);
    return await openDatabase(fullPath, version: 1, onCreate: _createTables);
  }

  Future<void> _createTables(Database db, int version) async {
    await db.execute('''
      CREATE TABLE planets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        distance REAL NOT NULL,
        size REAL NOT NULL,
        nickname TEXT
      )
    ''');
  }

  Future<int> addPlanet(Planet planet) async {
    final db = await instance.database;
    return await db.insert('planets', planet.toMap());
  }

  Future<List<Planet>> getPlanets() async {
    final db = await instance.database;
    final data = await db.query('planets');
    return data
        .map((entry) => Planet(
              id: entry['id'] as int?,
              name: entry['name'] as String,
              distance: entry['distance'] as double,
              size: entry['size'] as double,
              nickname: entry['nickname'] as String?,
            ))
        .toList();
  }

  Future<int> updatePlanet(Planet planet) async {
    final db = await instance.database;
    return await db.update(
      'planets',
      planet.toMap(),
      where: 'id = ?',
      whereArgs: [planet.id],
    );
  }

  Future<int> deletePlanet(int id) async {
    final db = await instance.database;
    return await db.delete('planets', where: 'id = ?', whereArgs: [id]);
  }
}

class PlanetManagerApp extends StatefulWidget {
  const PlanetManagerApp({Key? key}) : super(key: key);

  @override
  State<PlanetManagerApp> createState() => _PlanetManagerAppState();
}

class _PlanetManagerAppState extends State<PlanetManagerApp> {
  late Future<List<Planet>> planets;

  @override
  void initState() {
    super.initState();
    planets = DatabaseService.instance.getPlanets();
  }

  Future<void> refreshPlanets() async {
    final updatedList = await DatabaseService.instance.getPlanets();
    setState(() {
      planets = Future.value(updatedList);
    });
  }

  void showPlanetForm(BuildContext context, {Planet? planet}) {
    final nameController = TextEditingController(text: planet?.name ?? '');
    final distanceController =
        TextEditingController(text: planet?.distance.toString() ?? '');
    final sizeController =
        TextEditingController(text: planet?.size.toString() ?? '');
    final nicknameController =
        TextEditingController(text: planet?.nickname ?? '');

    showDialog(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: Text(planet == null ? 'Add Planet' : 'Edit Planet'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: nameController, decoration: const InputDecoration(labelText: 'Planet Name')),
              TextField(controller: distanceController, decoration: const InputDecoration(labelText: 'Distance (AU)'), keyboardType: TextInputType.number),
              TextField(controller: sizeController, decoration: const InputDecoration(labelText: 'Size (km)'), keyboardType: TextInputType.number),
              TextField(controller: nicknameController, decoration: const InputDecoration(labelText: 'Nickname (Optional)')),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                final name = nameController.text;
                final distance = double.tryParse(distanceController.text);
                final size = double.tryParse(sizeController.text);
                final nickname = nicknameController.text.isEmpty ? null : nicknameController.text;

                if (name.isEmpty || distance == null || size == null || distance <= 0 || size <= 0) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Please enter valid values.')),
                  );
                  return;
                }

                final newPlanet = Planet(
                  id: planet?.id,
                  name: name,
                  distance: distance,
                  size: size,
                  nickname: nickname,
                );

                if (planet == null) {
                  await DatabaseService.instance.addPlanet(newPlanet);
                } else {
                  await DatabaseService.instance.updatePlanet(newPlanet);
                }

                Navigator.of(dialogContext).pop();
                refreshPlanets();
              },
              child: Text(planet == null ? 'Add' : 'Save'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Planet Manager')),
      body: FutureBuilder<List<Planet>>(
        future: planets,
        builder: (context, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          if (snapshot.data!.isEmpty) return const Center(child: Text('No planets added.'));
          return ListView.builder(
            itemCount: snapshot.data!.length,
            itemBuilder: (context, index) {
              final planet = snapshot.data![index];
              return ListTile(
                title: Text(planet.name),
                subtitle: Text(planet.nickname ?? 'No nickname'),
                onTap: () => showPlanetForm(context, planet: planet),
                trailing: IconButton(
                  icon: const Icon(Icons.delete),
                  onPressed: () async {
                    await DatabaseService.instance.deletePlanet(planet.id!);
                    refreshPlanets();
                  },
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => showPlanetForm(context),
        child: const Icon(Icons.add),
      ),
    );
  }
}
