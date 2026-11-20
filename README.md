# Retraite+ — simulateur pédagogique de retraite

Application web Java / Spring Boot / Thymeleaf destinée à un projet universitaire MIAGE.

## Fonctionnalités incluses
- Formulaire de saisie d'un salarié.
- Calcul pédagogique de la pension de base à partir du SAM, du taux et de la proratisation.
- Paramétrage de la décote, de la surcote et de la majoration de 10 % pour trois enfants ou plus.
- Résultat détaillé et graphique.
- Tests unitaires de base.

## Prérequis
- JDK 17 ou supérieur.
- Maven 3.8+ (ou utiliser le wrapper Maven d'IntelliJ si disponible).
- Connexion Internet au premier lancement pour télécharger les dépendances et charger Bootstrap / Chart.js depuis leurs CDN.

## Lancer dans IntelliJ IDEA
1. Décompresser `retraite-plus.zip`.
2. Ouvrir le dossier contenant `pom.xml` avec IntelliJ IDEA.
3. Accepter l'import Maven.
4. Vérifier que le projet utilise un JDK 17 ou 21 : `File > Project Structure > Project SDK`.
5. Lancer `RetraitePlusApplication.java`.
6. Ouvrir http://localhost:8080.

En ligne de commande :
```bash
mvn spring-boot:run
```

Tests :
```bash
mvn test
```

## Formule pédagogique utilisée
Pension annuelle = SAM × taux de liquidation ajusté × min(trimestres acquis / trimestres requis, 1).

La décote est paramétrée par l'utilisateur : nombre de trimestres manquants × taux de réduction par trimestre.
La surcote est paramétrée à 1,25 % par trimestre éligible.
La majoration de 10 % est appliquée uniquement si l'utilisateur coche l'option et indique au moins 3 enfants.

## Limites importantes
Ce prototype n'est PAS un simulateur officiel. Il ne détermine pas automatiquement :
- l'âge légal ou le nombre de trimestres requis selon la génération ;
- les périodes cotisées / assimilées et les majorations de durée d'assurance ;
- le SAM à partir de l'historique réel des salaires ;
- les conditions d'ouverture du droit à décote, surcote, carrière longue, inaptitude, handicap ou dispositifs particuliers ;
- les prélèvements sociaux, minimum contributif, pension de réversion ou retraite complémentaire AGIRC-ARRCO.

Les données de taux et de trimestres sont saisies manuellement afin d'éviter de présenter comme certaines des règles qui varient selon la génération et la situation. Pour un projet final, documentez les règles avec les supports de cours et les sources officielles de l'Assurance retraite, puis implémentez des tables de paramètres datées et testées.

## Pistes d'amélioration pour le rendu final
1. Ajouter une table `RegleGeneration` (année de naissance, âge légal, trimestres requis).
2. Ajouter l'historique annuel des salaires et calculer le SAM avec plafonds et coefficients de revalorisation.
3. Ajouter les périodes assimilées, majorations enfants et départs anticipés.
4. Ajouter un module AGIRC-ARRCO séparé (points × valeur de service du point).
5. Ajouter une comparaison de plusieurs dates de départ et un export PDF.
6. Ajouter PostgreSQL et Spring Data JPA si la persistance est attendue.

## Avertissement
Les résultats sont des estimations pédagogiques et ne doivent pas être utilisés pour prendre une décision de départ à la retraite.
