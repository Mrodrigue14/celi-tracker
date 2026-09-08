# celi-tracker

Application Android de suivi des droits de cotisation **CELI** (compte d'épargne
libre d'impôt) et **CELIAPP** (compte d'épargne libre d'impôt pour l'achat d'une
première propriété), pour un usage personnel.

Elle remplace un classeur Excel dont les droits année par année étaient saisis à
la main plutôt que calculés — un chiffre corrigé dans le journal des
transactions désynchronisait silencieusement tout le reste.

## Principe

**Rien de calculé n'est stocké.** La base ne contient que des saisies brutes :
le profil, la table des plafonds annuels, le journal des dépôts et retraits. Les
droits de cotisation sont une fonction pure de ces trois entrées, recalculée à
chaque affichage. Il n'existe aucune table de droits par année.

Deux moteurs distincts, jamais un moteur unique paramétré par un drapeau : les
règles CELI et CELIAPP divergent sur chaque axe — début de l'accumulation,
restitution des droits après un retrait, plafond à vie, report des droits
inutilisés.

## État

En conception. Le design est dans
[`docs/superpowers/specs/`](docs/superpowers/specs/2026-09-07-suivi-celi-celiapp-design.md).

Ordre de livraison prévu :

0. Installation du SDK Android en ligne de commande + wrapper Gradle
1. Moteur de calcul (module Kotlin pur) + tests
2. Persistance Room + export / import JSON
3. Interface Compose
4. Lecture des plafonds de l'ARC + sauvegarde automatique Android
5. APK

## Avertissement

Ce projet ne donne aucun conseil fiscal ou financier. Les montants qu'il affiche
sont des calculs à partir de données saisies par l'utilisateur et n'ont aucune
valeur officielle. La source de vérité sur vos droits de cotisation reste
**Mon dossier** de l'Agence du revenu du Canada.

## Confidentialité

Ce dépôt est public et ne contient **aucune donnée financière nominative**. Les
scénarios de test sont anonymes. Les bases de données et les exports JSON sont
exclus par `.gitignore`.

## Licence

[PolyForm Shield License 1.0.0](LICENSE) — usage, modification et
redistribution libres, à l'exception de la construction d'un produit qui
concurrence celui du concédant. Cette restriction n'expire pas.
