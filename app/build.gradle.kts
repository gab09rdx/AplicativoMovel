import 'package:flutter/material.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

void main() {
  runApp(MaterialApp(home: PlanetasApp(), debugShowCheckedModeBanner: false));
}

class Planeta {
  int? id;
  String nome;
  double distancia;
  double tamanho;
  String? apelido;

  Planeta({
    this.id,
    required this.nome,
    required this.distancia,
    required this.tamanho,
    this.apelido,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'nome': nome,
      'distancia': distancia,
      'tamanho': tamanho,
      'apelido': apelido,
    };
  }
}

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;

  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('planetas.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(path, version: 1, onCreate: _createDB);
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE planetas (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nome TEXT NOT NULL,
        distancia REAL NOT NULL,
        tamanho REAL NOT NULL,
        apelido TEXT
      )
    ''');
  }

  Future<int> insertPlaneta(Planeta planeta) async {
    final db = await instance.database;
    return await db.insert('planetas', planeta.toMap());
  }

  Future<List<Planeta>> fetchPlanetas() async {
    final db = await instance.database;
    final result = await db.query('planetas');
    return result
        .map(
          (json) => Planeta(
            id: json['id'] as int?,
            nome: json['nome'] as String,
            distancia: json['distancia'] as double,
            tamanho: json['tamanho'] as double,
            apelido: json['apelido'] as String?,
          ),
        )
        .toList();
  }

  Future<int> updatePlaneta(Planeta planeta) async {
    final db = await instance.database;
    return await db.update(
      'planetas',
      planeta.toMap(),
      where: 'id = ?',
      whereArgs: [planeta.id],
    );
  }

  Future<int> deletePlaneta(int id) async {
    final db = await instance.database;
    return await db.delete('planetas', where: 'id = ?', whereArgs: [id]);
  }
}

class PlanetasApp extends StatefulWidget {
  @override
  _PlanetasAppState createState() => _PlanetasAppState();
}

class _PlanetasAppState extends State<PlanetasApp> {
  late Future<List<Planeta>> planetas;

  @override
  void initState() {
    super.initState();
    planetas = DatabaseHelper.instance.fetchPlanetas();
  }

  Future<void> _refresh() async {
    final updatedPlanetas = await DatabaseHelper.instance.fetchPlanetas();
    setState(() {
      planetas = Future.value(updatedPlanetas);
    });
  }

  void _mostrarFormulario(BuildContext context, {Planeta? planeta}) {
    final nomeController = TextEditingController(text: planeta?.nome ?? '');
    final distanciaController = TextEditingController(
      text: planeta?.distancia.toString() ?? '',
    );
    final tamanhoController = TextEditingController(
      text: planeta?.tamanho.toString() ?? '',
    );
    final apelidoController = TextEditingController(
      text: planeta?.apelido ?? '',
    );

    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text(planeta == null ? 'Adicionar Planeta' : 'Editar Planeta'),
          content: SingleChildScrollView(
            child: Column(
              children: [
                TextField(
                  controller: nomeController,
                  decoration: InputDecoration(labelText: 'Nome do Planeta'),
                ),
                TextField(
                  controller: distanciaController,
                  decoration: InputDecoration(
                    labelText: 'Distância do Sol (UA)',
                  ),
                  keyboardType: TextInputType.number,
                ),
                TextField(
                  controller: tamanhoController,
                  decoration: InputDecoration(labelText: 'Tamanho (km)'),
                  keyboardType: TextInputType.number,
                ),
                TextField(
                  controller: apelidoController,
                  decoration: InputDecoration(labelText: 'Apelido (Opcional)'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text('Cancelar'),
            ),
            ElevatedButton(
              onPressed: () async {
                final nome = nomeController.text;
                final distancia = double.tryParse(distanciaController.text);
                final tamanho = double.tryParse(tamanhoController.text);
                final apelido =
                    apelidoController.text.isEmpty
                        ? null
                        : apelidoController.text;

                if (nome.isEmpty ||
                    distancia == null ||
                    tamanho == null ||
                    distancia <= 0 ||
                    tamanho <= 0) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Preencha os campos corretamente')),
                  );
                  return;
                }

                final novoPlaneta = Planeta(
                  id: planeta?.id,
                  nome: nome,
                  distancia: distancia,
                  tamanho: tamanho,
                  apelido: apelido,
                );

                bool sucesso;
                String mensagem;
                if (planeta == null) {
                  sucesso =
                      await DatabaseHelper.instance.insertPlaneta(novoPlaneta) >
                      0;
                  mensagem =
                      sucesso
                          ? 'Planeta adicionado com sucesso!'
                          : 'Falha ao adicionar planeta.';
                } else {
                  sucesso =
                      await DatabaseHelper.instance.updatePlaneta(novoPlaneta) >
                      0;
                  mensagem =
                      sucesso
                          ? 'Planeta atualizado com sucesso!'
                          : 'Falha ao atualizar planeta.';
                }

                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(SnackBar(content: Text(mensagem)));

                if (sucesso) {
                  Navigator.of(context).pop();
                  await _refresh();
                }
              },
              child: Text(planeta == null ? 'Adicionar' : 'Salvar'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Gerenciador de Planetas')),
      body: FutureBuilder<List<Planeta>>(
        future: planetas,
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return Center(child: CircularProgressIndicator());
          }
          if (snapshot.data!.isEmpty) {
            return Center(child: Text('Nenhum planeta cadastrado'));
          }
          return ListView.builder(
            itemCount: snapshot.data!.length,
            itemBuilder: (context, index) {
              final planeta = snapshot.data![index];
              return ListTile(
                title: Text(planeta.nome),
                subtitle: Text(planeta.apelido ?? 'Sem apelido'),
                onTap: () => _mostrarFormulario(context, planeta: planeta),
                trailing: IconButton(
                  icon: Icon(Icons.delete),
                  onPressed: () async {
                    bool sucesso =
                        await DatabaseHelper.instance.deletePlaneta(
                          planeta.id!,
                        ) >
                        0;
                    String mensagem =
                        sucesso
                            ? 'Planeta excluído com sucesso!'
                            : 'Falha ao excluir planeta.';
                    ScaffoldMessenger.of(
                      context,
                    ).showSnackBar(SnackBar(content: Text(mensagem)));

                    if (sucesso) {
                      await _refresh();
                    }
                  },
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _mostrarFormulario(context),
        child: Icon(Icons.add),
      ),
    );
  }
}