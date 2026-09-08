# Suivi des droits de cotisation CELI / CELIAPP — design

Date : 2026-09-07
Statut : en attente de revue

## Problème

Le suivi actuel est un classeur Excel (`gestion_CELI.xlsx`, 4 feuilles). Il
fonctionne, mais il comporte un défaut structurel : les colonnes
« Droits début d'année » et « Droits fin d'année » de 2023 à 2046 sont des
**valeurs saisies à la main**, pas des formules. Le classeur *documente* la
règle de report des retraits (« Les retraits d'une année s'ajoutent à vos
droits au 1er janvier suivant ») mais ne la calcule nulle part. Une seule
correction dans le journal des transactions désynchronise silencieusement tout
le tableau des droits.

Défauts secondaires constatés :

- Asymétrie entre les comptes : la feuille CELI tient un journal de
  cotisations mais aucun suivi de placements ; la feuille CELIAPP tient un
  suivi de placements mais aucun journal de cotisations (« Vos cotisations
  totales à ce jour » est figé à 0, donc « droits restants » affiche toujours
  40 000 $).
- Aucune détection de sur-cotisation, alors que la pénalité de l'ARC (1 % par
  mois sur l'excédent) est le risque réel de ce suivi.
- Rien sur l'échéance CELIAPP : ni date d'ouverture, ni compte à rebours
  15 ans / 71 ans, bien que la règle soit écrite dans la feuille.
- Les plafonds annuels futurs sont à 0 et doivent être mis à jour à la main.

## Périmètre

**Dans le périmètre.** Suivi des droits de cotisation CELI et CELIAPP :
journal des dépôts et retraits, calcul exact des droits année par année,
report des retraits, détection de sur-cotisation, échéance CELIAPP.

**Hors périmètre.** Le suivi de portefeuille boursier des feuilles
« Non enregistré » et « CELIAPP » (achats, ventes, gain/perte, gain en capital
imposable) n'est pas repris. Pas de cours du marché, pas de positions
ouvertes, pas de valeur marchande.

**Utilisateur unique.** Aucun compte, aucune authentification, aucun backend à
construire. Une seule personne, un seul appareil.

**Plateforme.** Application Android native — Kotlin, Jetpack Compose, Room.
Le poste de développement dispose de JDK 21 ; le SDK Android est installé en
ligne de commande (`cmdline-tools` / `sdkmanager`), sans Android Studio.

## Principe directeur : ne rien stocker de calculé

C'est la correction du défaut structurel de l'Excel. La base ne contient que
des **saisies brutes**. Les droits de cotisation sont une **fonction pure**
de `(profil, table des plafonds, transactions triées par date)`, recalculée à
chaque affichage.

Il n'existe donc **aucune** table `droits_par_annee`. En créer une
réintroduirait exactement le bug qu'on corrige.

Conséquence utile : puisque le solde est rejouable à n'importe quelle date, la
détection de sur-cotisation vient sans machinerie supplémentaire — la pénalité
de l'ARC porte sur l'excédent le plus élevé de chaque mois, ce qui exige
précisément un solde rejouable dans l'ordre chronologique.

## Modèle de données

```
Profil (singleton)
  anneeAdmissibiliteCeli : Int        // année des 18 ans + résidence
  anneeNaissance         : Int        // pour la règle des 71 ans (CELIAPP)
  dateOuvertureCeliapp   : LocalDate? // début de l'accumulation ET horloge 15 ans

PlafondAnnuel
  compte   : Compte        // CELI | CELIAPP
  annee    : Int
  montant  : BigDecimal
  source   : Source        // MANUEL | ARC_AUTO
  confirme : Boolean       // un plafond non confirmé n'entre pas dans le calcul

Transaction
  id          : Long
  compte      : Compte
  date        : LocalDate
  type        : TypeTx     // DEPOT | RETRAIT
  montant     : BigDecimal // toujours positif
  institution : String?
  notes       : String?

SnapshotArc
  id             : Long
  compte         : Compte
  dateReference  : LocalDate
  droitsDeclares : BigDecimal

Reglages (singleton)
  urlPageArc               : String   // éditable, pas une constante compilée
  dateDerniereVerification : Instant?
```

Les montants sont en `BigDecimal`, jamais en `Double` : les droits de
cotisation sont des sommes d'argent exactes, et une dérive de virgule flottante
sur une comparaison de sur-cotisation est inacceptable.

### Le snapshot ARC est informatif, jamais prioritaire

L'utilisateur saisit périodiquement le montant de droits que l'ARC affiche,
avec sa date de référence. L'application montre côte à côte le chiffre calculé
et le chiffre déclaré, et met l'écart en évidence.

Le snapshot **ne remplace jamais** le calcul. Les chiffres de l'ARC retardent
sur le traitement des déclarations et n'incluent typiquement pas les
cotisations de l'année courante ; les laisser écraser le calcul dédoublerait
ou effacerait des cotisations récentes. L'interface affiche toujours la date de
référence pour que la fraîcheur du chiffre soit lisible.

## Moteur CELI

```
droitsFin(anneeAdmissibilite - 1) = 0
droitsDebut(A) = droitsFin(A - 1) + plafond(A) + retraits(A - 1)
droitsFin(A)   = droitsDebut(A) - depots(A)
```

Les retraits d'une année donnée reviennent aux droits le **1er janvier de
l'année suivante**, pas immédiatement.

**Sur-cotisation.** Pour chaque mois, l'excédent est
`max(0, cotisations cumulées - droits disponibles à cette date)`. La pénalité
de l'ARC est de 1 % par mois, appliquée à l'excédent le plus élevé du mois.
L'application signale l'excédent et estime la pénalité ; elle ne produit aucun
formulaire fiscal.

## Moteur CELIAPP — séparé, pas un CELI avec un drapeau

Les deux régimes divergent sur chaque axe. Partager un moteur paramétré par un
drapeau est la façon la plus probable de corrompre silencieusement l'un des
deux comptes.

| | CELI | CELIAPP |
|---|---|---|
| Début de l'accumulation | 18 ans + résidence, même sans compte ouvert | **à l'ouverture du compte** |
| Retrait | redonne des droits le 1er janvier suivant | **ne redonne jamais de droits** |
| Plafond à vie | aucun | 40 000 $ |
| Report des droits inutilisés | illimité | plafonné à 8 000 $ |

```
droitsAnnee(A) = min( 8000 + min(reportInutilise(A), 8000),
                      40000 - cotisationsCumulees(avant A) )
report(A + 1)  = min( droitsAnnee(A) - depots(A), 8000 )
```

Le plafond annuel de 8 000 $ et le plafond à vie de 40 000 $ sont fixés par la
loi et **ne sont pas indexés** : rien à récupérer automatiquement côté CELIAPP.

**Le report ne se cumule pas.** C'est le piège de ce régime, et la raison d'être
du `min(reportInutilise, 8000)` ci-dessus. Contrairement au CELI et au REER, où
les droits inutilisés s'accumulent indéfiniment, le report CELIAPP est plafonné
à 8 000 $ **par année d'arrivée**. Une personne qui ouvre un compte et ne cotise
jamais n'a pas 8 000 $ de plus chaque année : son plafond annuel se stabilise à
16 000 $ et n'augmente plus. Le plafond à vie de 40 000 $, lui, reste intact —
il faut donc au minimum trois années de cotisation (16 000 + 16 000 + 8 000)
pour l'épuiser.

**Période de participation maximale.** Elle se termine le 31 décembre de l'année
où survient le **premier** des trois événements suivants :

1. le 15e anniversaire de l'ouverture du premier CELIAPP ;
2. les 71 ans du titulaire ;
3. l'année suivant le premier retrait admissible.

L'application calcule les branches 1 et 2 et alerte à l'approche. La branche 3
n'est pas implémentée — voir les exclusions assumées ci-dessous.

### Exclusions assumées

Ce sont des décisions, pas des oublis.

- **Les retraits CELIAPP réduisent le solde et ne touchent jamais aux droits.**
  La distinction entre retrait admissible (achat d'une première propriété, non
  imposable) et retrait ordinaire (imposable) n'est pas suivie.
- **La règle « l'année suivant le premier retrait admissible » n'est pas
  implémentée** dans le calcul d'échéance, puisqu'elle dépend de la
  classification des retraits ci-dessus. L'échéance réelle peut donc être plus
  rapprochée que celle affichée.
- **La déduction fiscale CELIAPP n'est pas suivie** (montant réclamé vs reporté
  à une année future).

## Mise à jour des plafonds CELI

Il n'existe aucune API de l'ARC pour le plafond CELI. La page officielle
présente les montants en texte narratif, pas en tableau structuré. Le plafond
est de 5 000 $ indexé à l'inflation et **arrondi au 500 $ près** ; il change
d'un cran tous les deux ou trois ans (7 000 $ pour 2024, 2025 et 2026).

L'application lit la page de l'ARC, mais est conçue pour **échouer
visiblement** :

1. **Validation stricte.** Un montant lu est rejeté s'il n'est pas un multiple
   de 500, s'il est inférieur au plafond de l'année précédente, ou s'il dépasse
   ce dernier de plus de 2 000 $. Ces invariants attrapent l'essentiel des
   erreurs d'extraction.
2. **Proposition, jamais application.** Un plafond lu est enregistré avec
   `source = ARC_AUTO` et `confirme = false`. Un plafond non confirmé n'entre
   pas dans le calcul des droits. L'utilisateur confirme dans les Réglages.
3. **Échec visible.** Réseau indisponible, page refondue, ou montant rejeté →
   bannière « lecture automatique échouée », avec le bouton d'ouverture de la
   page de l'ARC et le champ de saisie manuelle. Le repli manuel est un
   comportement affiché, pas un mode dégradé silencieux.
4. **URL éditable.** `Reglages.urlPageArc` est une donnée, pas une constante
   compilée : une réorganisation du site de l'ARC se corrige dans
   l'application, sans nouvelle version.
5. **Fréquence.** Au plus une vérification par mois, et seulement s'il manque
   un plafond pour l'année courante ou suivante.

## Sauvegarde et récupération

Un suivi qui ne survit pas à la perte du téléphone serait une régression par
rapport à un classeur Excel synchronisé. Deux mécanismes, aux modes de
défaillance différents :

- **Sauvegarde automatique Android** (`allowBackup`). Android copie la base de
  l'application vers le Google Drive de l'utilisateur et la restaure à la
  réinstallation. Coût : un attribut de manifeste.
  *Réserve documentée :* la restauration est déclenchée par une installation
  via le Play Store ou l'assistant de configuration. L'application étant
  installée par APK chargé manuellement, la restauration automatique peut ne
  pas se déclencher. Le contenu de la sauvegarde n'est pas inspectable.
- **Export / import JSON manuel.** Un bouton « Exporter » produit un fichier
  lisible que l'utilisateur range où il veut ; « Importer » le relit. C'est le
  seul mécanisme dont on peut vérifier le bon fonctionnement **avant** d'en
  avoir besoin.

## Écrans

1. **Accueil** — deux cartes (CELI, CELIAPP) : droits restants, cotisé cette
   année, alerte de sur-cotisation, écart avec le snapshot ARC.
2. **Détail d'un compte** — le tableau année par année (plafond, droits début,
   dépôts, retraits, droits fin), calculé et non stocké. Pour le CELIAPP :
   report, plafond à vie restant, échéance.
3. **Journal** — transactions du compte : ajout, modification, suppression.
4. **Réglages** — profil, table des plafonds éditable, plafonds proposés à
   confirmer, snapshot ARC, URL de la page ARC, export / import.

## Tests

Le moteur de calcul est un module Kotlin pur, sans dépendance Android, testable
en ligne de commande. Il est écrit et vérifié **avant** toute interface.

**Fixture d'acceptation `scenario_2019_eligible_three_deposits`.** Une personne
devenue admissible au CELI en 2019, sans aucun retrait, avec trois dépôts :
5 000,00 $ (2021-03-10), 3 500,00 $ (2023-06-15), 1 200,00 $ (2024-11-02).
Plafonds 2019→2026 : 6 000, 6 000, 6 000, 6 000, 6 500, 7 000, 7 000, 7 000.

Résultats attendus, année par année :

| Année | Droits début | Dépôts | Droits fin |
|---|---|---|---|
| 2019 | 6 000,00 | 0 | 6 000,00 |
| 2020 | 12 000,00 | 0 | 12 000,00 |
| 2021 | 18 000,00 | 5 000,00 | 13 000,00 |
| 2022 | 19 000,00 | 0 | 19 000,00 |
| 2023 | 25 500,00 | 3 500,00 | 22 000,00 |
| 2024 | 29 000,00 | 1 200,00 | 27 800,00 |
| 2025 | 34 800,00 | 0 | 34 800,00 |
| 2026 | 41 800,00 | 0 | **41 800,00** |

Scénario synthétique, dérivé des plafonds annuels publiés par l'ARC. Contrôle :
cumul des plafonds 51 500 $ − dépôts 9 700 $ = 41 800 $.

**Fixture d'acceptation `scenario_fhsa_opened_2023_no_contributions`.** Un
CELIAPP ouvert en avril 2023, sans aucune cotisation ni retrait depuis.

| Année | Droits disponibles | Report vers A+1 |
|---|---|---|
| 2023 | 8 000 | 8 000 |
| 2024 | 16 000 | 8 000 |
| 2025 | 16 000 | 8 000 |
| 2026 | **16 000** | — |

Plafond à vie restant : 40 000 $. Fin de la période de participation :
**2038-12-31** (15e anniversaire en avril 2038).

Ce scénario est le garde-fou contre l'erreur la plus tentante du régime : croire
que trois années sans cotiser accumulent 32 000 $ de droits. Le report est
plafonné à 8 000 $ par année d'arrivée et ne se cumule pas, donc le plafond
annuel se stabilise à 16 000 $. Un moteur qui renvoie 32 000 $ pour 2026 est
faux.

**Fixtures synthétiques.** Le scénario d'acceptation ne contient aucun retrait :
le chemin de restitution des droits n'est couvert par aucune de ses fixtures et
doit être testé explicitement.

- Retrait en année A → droits restitués le 1er janvier de A+1, et **pas avant**.
- Retrait puis re-dépôt la même année → sur-cotisation détectée.
- Sur-cotisation sur plusieurs mois → pénalité calculée sur l'excédent maximal
  de chaque mois.
- CELIAPP : cotisation partielle en année A → report plafonné à 8 000 $ en A+1,
  donc 16 000 $ cotisables au maximum.
- CELIAPP : retrait → le solde baisse, les droits ne bougent pas.
- CELIAPP : plafond à vie de 40 000 $ atteint → droits annuels ramenés à 0.
- Plafond d'une année absent ou non confirmé → l'année est signalée comme
  incomplète, aucun droit inventé.
- Extraction ARC : montant non multiple de 500, en baisse, ou en hausse de plus
  de 2 000 $ → rejeté.

## Ordre de livraison

0. Script d'installation du SDK Android (`cmdline-tools` + `sdkmanager`), sans
   Android Studio. Wrapper Gradle.
1. **Moteur de calcul** — module Kotlin pur + tests, dont la fixture
   d'acceptation. Vérifiable en ligne de commande, sans téléphone.
2. Persistance Room + export / import JSON.
3. Interface Compose.
4. Lecture des plafonds ARC + sauvegarde automatique Android.
5. APK installé.

## Sources des règles

- Fermeture du CELIAPP et période de participation maximale (les trois branches
  de l'échéance) :
  <https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account/closing-your-fhsa.html>
- Plafond annuel CELI et calcul des droits :
  <https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/tax-free-savings-account/contributing/calculate-room.html>

Le plafond de report CELIAPP de 8 000 $ non cumulatif, le plafond annuel de
8 000 $ et le plafond à vie de 40 000 $ sont fixés par la loi et ne sont pas
indexés.

## Confidentialité

Le dépôt est public. Aucune donnée financière nominative n'y entre : les
fixtures de test sont des scénarios anonymes, et le journal réel (base de
données, exports JSON) est exclu par `.gitignore` dès le premier commit.
