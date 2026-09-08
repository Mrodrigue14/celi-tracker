# CLAUDE.md

Guidance pour Claude Code (claude.ai/code) dans ce dépôt.

## Projet

Application Android personnelle (Kotlin, Jetpack Compose, Room) de suivi des
droits de cotisation CELI et CELIAPP. Remplace un classeur Excel. Utilisateur
unique, aucun backend, aucune authentification.

Le design de référence est
`docs/superpowers/specs/2026-09-07-suivi-celi-celiapp-design.md`. Lis-le avant
toute modification du moteur de calcul.

Le développement se fait sans Android Studio : JDK 21 + SDK Android installé par
`sdkmanager` (`cmdline-tools`).

## Invariants — à ne pas casser

**1. Rien de calculé n'est stocké.** La base contient le profil, la table des
plafonds et le journal des transactions. Rien d'autre. Les droits sont une
fonction pure `(profil, plafonds, transactions triées par date) → droits`,
recalculée à la lecture. Ajouter une table `droits_par_annee` ou mettre en cache
un solde en base réintroduit exactement le bug du classeur Excel que ce projet
corrige.

**2. Deux moteurs séparés, CELI et CELIAPP.** Ne pas les fusionner en un moteur
paramétré par un drapeau de type de compte. Les règles divergent partout :

| | CELI | CELIAPP |
|---|---|---|
| Début de l'accumulation | 18 ans + résidence, même sans compte | à l'ouverture du compte |
| Retrait | redonne des droits le 1er janvier suivant | ne redonne **jamais** de droits |
| Plafond à vie | aucun | 40 000 $ |
| Report des droits inutilisés | illimité, cumulatif | plafonné à 8 000 $, **non cumulatif** |

Le report CELIAPP est le piège du régime : il est plafonné à 8 000 $ *par année
d'arrivée*, donc il ne s'accumule pas. Trois années sans cotiser ne donnent pas
32 000 $ de droits — le plafond annuel se stabilise à 16 000 $. Écrire
`report += reste` au lieu de `report = min(reste, 8000)` produit un chiffre
faux et plausible.

Réutiliser le chemin de restitution du CELI pour le CELIAPP est le bug de
correctness le plus probable de ce projet.

**3. `BigDecimal`, jamais `Double`.** Ce sont des sommes d'argent exactes, et
une dérive de virgule flottante sur une comparaison de sur-cotisation est
inacceptable.

**4. Un plafond non confirmé n'entre pas dans le calcul.** Un plafond lu
automatiquement sur le site de l'ARC est enregistré avec `confirme = false` et
reste inerte jusqu'à validation par l'utilisateur. Aucune modification
silencieuse des droits.

**5. Aucune donnée financière nominative dans le dépôt.** Vérifie-le
mécaniquement avant de committer : `bash tools/verifier-confidentialite.sh`.
Les motifs privés vivent dans `local-data/motifs-prives.txt`, gitignoré — les
écrire dans un fichier suivi les publierait, ce qui est précisément le
problème. Sans ce fichier le script ne vérifie rien et le dit. Il est public. Les
fixtures de test sont des scénarios anonymes, sans nom d'institution ni
formulation à la première personne. Les bases et les exports JSON sont exclus
par `.gitignore`.

## Test d'acceptation

`scenario_2019_eligible_three_deposits` — admissibilité 2019, aucun retrait,
dépôts de 5 000,00 $ (2021-03-10), 3 500,00 $ (2023-06-15) et 1 200,00 $
(2024-11-02), plafonds 6 000 / 6 000 / 6 000 / 6 000 / 6 500 / 7 000 / 7 000 /
7 000 (2019 à 2026).

Droits fin 2026 attendus : **41 800,00 $**. Scénario synthétique, dérivé des
plafonds annuels publiés par l'ARC. Contrôle : cumul des plafonds 51 500 $ −
dépôts 9 700 $ = 41 800 $.

`scenario_fhsa_opened_2023_no_contributions` — CELIAPP ouvert en avril 2023,
aucune cotisation. Droits 2026 attendus : **16 000 $**, pas 32 000 $. Plafond à
vie restant 40 000 $. Fin de période de participation : **2038-12-31**.

Ce scénario ne contient **aucun retrait**, donc le chemin de restitution des
droits n'a aucune validation externe : il est couvert uniquement par des
fixtures synthétiques, à traiter avec la même rigueur.

## Git

`main` est protégée : le check « Build & test » doit passer, et la branche doit
être à jour avec `main` avant le merge. Travailler en branche, ouvrir une PR,
laisser la CI verte avant de merger.

Les PRs Dependabot patch et mineures sont auto-mergées une fois la CI verte.
Les montées majeures ne sont jamais auto-mergées : `.github/dependabot.yml`
empêche Dependabot d'en ouvrir pour Gradle, et le `if:` du workflow
d'auto-merge exclut toute majeure pour les autres écosystèmes. Elles restent
en revue manuelle.

## Contraintes de versions

**Kotlin est plafonné par CodeQL, pas par Gradle.** L'extracteur Kotlin de
CodeQL refuse toute version qu'il ne connaît pas encore
(`KotlinVersionTooRecentError`), et `Analyze (java-kotlin)` est un check requis
sur `main`. Une montée de Kotlin trop en avance échoue donc en CI — c'est
voulu, et c'est le check qui fait autorité. Aucun plafond n'est figé dans
`dependabot.yml` : la PR reste simplement bloquée jusqu'à ce que CodeQL
rattrape, ce qui s'auto-résout sans maintenance.

Constaté le 2026-09-08 : Kotlin 2.4.20 rejeté, 2.4.10 accepté.

## Licence

PolyForm Shield License 1.0.0 — libre d'usage, de modification et de
redistribution, sauf pour bâtir un produit concurrent. Ne pas relicencier ni
retirer la mention de droit d'auteur (`Required Notice`) en tête de `LICENSE`.

Aucun code tiers n'est repris dans ce dépôt : il n'y a donc pas de `NOTICE` ni
de `third_party/`. Si du code sous une autre licence est intégré plus tard, il
faudra les ajouter.
