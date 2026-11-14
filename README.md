# Industrial Facilities

Mod de construction et d’automatisation industrielle pour Minecraft 1.21.1 avec NeoForge. Le fichier `industrial_facilities.mcreator` ouvre le projet dans MCreator.

## Où lire le code

Les noms de fichiers ci-dessous sont relatifs à [`src/main/java/fr/vengelis/industrialfacilities`](src/main/java/fr/vengelis/industrialfacilities).

| Fonction | Par où commencer | Ce qu’on y trouve |
| --- | --- | --- |
| Sas | [`AirlockAutomationManager`](src/main/java/fr/vengelis/industrialfacilities/airlock/AirlockAutomationManager.java), [`AirlockDoorManager`](src/main/java/fr/vengelis/industrialfacilities/airlock/AirlockDoorManager.java) | Détection de joueurs et de signal redstone, validation du rectangle, pose et retrait des panneaux. |
| Portes industrielles | [`IndustrialDoorManager`](src/main/java/fr/vengelis/industrialfacilities/door/IndustrialDoorManager.java), [`IndustrialDoorEntity`](src/main/java/fr/vengelis/industrialfacilities/door/IndustrialDoorEntity.java) | Assemblage du cadre, collision des vantaux, animation et restauration au chargement. |
| Lecteurs, badges et Keypass | [`AccessDoorService`](src/main/java/fr/vengelis/industrialfacilities/access/AccessDoorService.java), [`AccessRegistry`](src/main/java/fr/vengelis/industrialfacilities/access/AccessRegistry.java), [`BadgeBankData`](src/main/java/fr/vengelis/industrialfacilities/access/BadgeBankData.java) | Autorisation d’ouverture, liaisons côté serveur et données persistantes de la banque de badges. |
| Tablette | [`NetworkTabletClient`](src/main/java/fr/vengelis/industrialfacilities/security/client/NetworkTabletClient.java), [`TerminalLinks`](src/main/java/fr/vengelis/industrialfacilities/access/TerminalLinks.java) | Affichage des liaisons et stockage de leurs extrémités. |
| Caméras | [`SecurityCameraEntity`](src/main/java/fr/vengelis/industrialfacilities/security/SecurityCameraEntity.java), [`CameraTerminalEntity`](src/main/java/fr/vengelis/industrialfacilities/security/CameraTerminalEntity.java), [`CameraProgram`](src/main/java/fr/vengelis/industrialfacilities/security/CameraProgram.java) | Orientation et détection, exécution des paliers, validation et sauvegarde des programmes. |
| Fabricator | [`FabricatorGUIMenu`](src/main/java/fr/vengelis/industrialfacilities/world/inventory/FabricatorGUIMenu.java), [`FabricatorCrafting`](src/main/java/fr/vengelis/industrialfacilities/fabricator/FabricatorCrafting.java) | Validation des demandes et simulation de la fabrication avant modification de l’inventaire. |
| Alarmes, barrières et panneaux | [`AlarmBlockEntity`](src/main/java/fr/vengelis/industrialfacilities/alarm/AlarmBlockEntity.java), [`EnergyBarrierManager`](src/main/java/fr/vengelis/industrialfacilities/energybarrier/EnergyBarrierManager.java), [`PanelStructure`](src/main/java/fr/vengelis/industrialfacilities/panel/PanelStructure.java) | Diffusion du son, activation du champ, regroupement des panneaux lumineux. |

`elements/` contient les définitions MCreator. Vérifier le verrouillage d’un élément avant une modification Java, car une régénération peut réécrire ses fichiers. Les classes dans les dossiers `client/` gèrent l’affichage, les entrées et le son local. Les classes `*Menu` et `*Messages` font le lien avec les actions et les données côté serveur. Les clés écrites dans les `CompoundTag` constituent le format des sauvegardes Minecraft.

`src/main/resources/` contient les modèles, textures, sons, traductions et données de jeu. Les scripts dans `tools/` servent notamment à générer des ressources et à préparer les shaders compatibles.

## Compiler le code

Avec le JDK 21, depuis la racine du projet sous Windows :

```powershell
.\gradlew.bat compileJava
```

Sur un poste hors ligne qui dispose déjà du cache Gradle et du JAR JEI, ajouter `--offline` et `"-PjeiJar=C:\chemin\vers\jei.jar"`.
