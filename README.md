# Retraite+ — simulateur de retraite (régime général + Agirc-Arrco)

Application web Java / Spring Boot / Thymeleaf réalisée pour le projet de TD.
Le conseiller saisit le **relevé de carrière brut** d'un salarié. Le simulateur détermine **lui-même** :

- l'âge légal, la durée requise et l'âge du taux plein selon la génération (réformes 2010 et 2023, **suspension LFSS 2026**) ;
- les trimestres année par année : cotisés (calcul automatique avec le seuil de 150 h ou 200 h de SMIC de l'année), assimilés (chômage, service national, maladie, maternité…), majorations pour enfants, autres régimes ;
- les **anomalies de relevé** : trimestres déclarés différents de ce que permet le revenu ;
- la **retraite anticipée carrière longue** : condition de début de carrière (bornes 16, 18, 20 et 21 ans) et durée cotisée ou réputée cotisée avec ses plafonds ;
- le **SAM** : 25 meilleures années, salaires plafonnés au PASS de chaque année, conversion francs → euros, coefficients de revalorisation officiels de la Cnav ;
- le **taux** : taux plein (durée, âge, carrière longue, inaptitude), **décote** (0,625 % par trimestre, règle du plus favorable, 20 trimestres au maximum) ou **surcote** (1,25 % par trimestre) ;
- la **pension de base** : proratisation, plafond à 50 % du PASS, majoration de 10 % pour 3 enfants ou plus, **minimum contributif** ;
- l'**Agirc-Arrco** : total des points (relevé et calcul automatique), valeur de service à la date de départ, coefficient d'anticipation en cas de décote, majoration enfants ;
- le **net** (CSG, CRDS, CASA, cotisation maladie de 1 %), le taux de remplacement, le **montant revalorisé à ce jour** si le départ est passé ;
- la **réversion** pour une personne veuve : 54 % au régime général sous plafond de ressources, 60 % des points Agirc-Arrco sans condition de ressources ;
- une **comparaison de plusieurs dates de départ** : carrière longue, âge légal, âge légal + 1 an, 67 ans.

Chaque résultat est expliqué pas à pas. Un tableau détaille chaque année de carrière et des graphiques accompagnent la présentation orale. La page résultat peut être imprimée en PDF.

## Lancer le projet
Prérequis : JDK 17 ou plus, Maven 3.8 ou plus (ou IntelliJ).

```bash
mvn spring-boot:run      # puis http://localhost:8080
mvn test                 # 17 tests unitaires du moteur
```
Dans IntelliJ : ouvrir le dossier contenant `pom.xml`, accepter l'import Maven, lancer `RetraitePlusApplication`.

Le bouton **« Charger le cas pratique M. Ousmane »** pré-remplit le formulaire avec le relevé du TD.

## Saisir un autre salarié
1. Renseigner sa date de naissance, son sexe et la date de départ envisagée (le 1er d'un mois).
2. Coller sa carrière dans la zone de texte, ou importer un CSV. Le modèle se télécharge depuis la page d'accueil.
   ```
   annee;type;revenu;devise;trimestres;points
   1999;SALAIRE;173640;FRF;4;231.78
   1983;SERVICE_NATIONAL;;;3;
   2025;SALAIRE;5111;EUR;;
   ```
   - `type` : SALAIRE, SERVICE_NATIONAL, CHOMAGE, MALADIE, MATERNITE, INVALIDITE, AUTRE ;
   - `trimestres` vide : calcul automatique à partir du revenu ;
   - `points` vide : calcul automatique à partir du salaire (années 2021 et suivantes) ;
   - plusieurs lignes pour une même année sont additionnées (plusieurs employeurs, période assimilée).
3. Pour un salarié encore en activité, indiquer un **salaire projeté** : les années manquantes jusqu'au départ sont ajoutées.
4. Compléter si besoin : enfants, trimestres d'autres régimes, taux de CSG, conjoint décédé.

## Résultat attendu pour le cas Ousmane (départ au 01/02/2025)
| Élément | Valeur |
|---|---|
| Génération 1963 | âge légal 62 ans et 9 mois, 170 trimestres requis |
| Trimestres | 166 cotisés + 5 assimilés = **171** (dont 2 en janvier 2025 : 5 111 € / 1 782 €) |
| Carrière longue | 8 trimestres à fin 1983 (≥ 5), 171 trimestres réputés cotisés ≥ 170 → **départ possible dès 60 ans, taux plein** |
| Anomalie | 2022 : 7 920 € de salaire valident 4 trimestres, le relevé n'en indique qu'1 → à régulariser |
| SAM (25 meilleures années, 1997-2021) | 42 487,77 € |
| Retraite de base | 21 243,89 €/an, soit **1 770,32 €/mois** |
| Agirc-Arrco | 11 203 points × 1,4386 € = **1 343,06 €/mois** |
| Total | **3 113,38 € brut**, environ 2 817 € net par mois ; environ 3 148 € brut après les revalorisations 2026 |
| Enfant | 1 seul enfant : pas de majoration de 10 % ; trimestres d'éducation à vérifier (enfant né avant 2010) |
| Veuvage | réversion à calculer si l'on connaît les droits de l'épouse décédée |

## Architecture
```
engine/       moteur de calcul en Java pur (indépendant de Spring, testable)
  RegleGeneration    âges, durées, bornes carrière longue
  CarriereParser     lecture de la carrière (CSV ou copier-coller Excel)
  Parametres         chargement des barèmes datés
  MoteurRetraite     chaîne de calcul complète et comparaison des dates
  ResultatSimulation résultat détaillé (lu par les vues)
service/      lien entre le formulaire et le moteur ; cas Ousmane
controller/   pages web, import de fichier, modèle CSV, mise en forme (fmt)
resources/parametres/   barèmes officiels datés (CSV) — à mettre à jour chaque année
resources/exemples/     cas pratique Ousmane
```

### Mettre à jour les paramètres (sans toucher au code)
| Fichier | Contenu | Source |
|---|---|---|
| `pass.csv` | PASS par année (FRF puis EUR) | Barèmes IPP |
| `seuil-trimestre.csv` | salaire validant un trimestre | Barèmes IPP, circulaire Cnav 2025-33 |
| `coefficients-salaires-2025.csv` / `-2026.csv` | revalorisation des salaires portés au compte | circulaires Cnav 2024-39 et 2025-29 |
| `revalorisation-pensions.csv` | revalorisations des pensions (1,022 en 2025, 1,009 en 2026) | Cnav |
| `agirc-valeur-service.csv` / `agirc-valeur-achat.csv` | valeurs du point | Agirc-Arrco |
| `constantes.properties` | décote, surcote, minimum contributif, réversion, CSG… | service-public, Cnav |

Pour une nouvelle année, il suffit d'ajouter une ligne, ou un fichier `coefficients-salaires-AAAA.csv` déclaré dans `Parametres`.

## Limites (affichées aussi sur la page résultat)
- Seul le régime général est calculé en détail. Les trimestres d'autres régimes ne comptent que pour le taux.
- Surcote approchée année par année. Le coefficient d'anticipation Agirc-Arrco suit le barème « trimestres manquants » uniquement.
- Minimum contributif, réversion et prélèvements utilisent les barèmes 2026.
- Pour les départs pendant la suspension, l'âge carrière longue « avant 20 ans » des générations 1965 à 1971 est à confirmer.
- Non pris en compte : rachat de trimestres, retraite progressive, cumul emploi-retraite, départs anticipés pour handicap ou incapacité, polypensionnés.

Estimation pédagogique, non officielle : à confronter au simulateur M@rel (info-retraite.fr).
