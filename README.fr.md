# celi-tracker

[![English](https://img.shields.io/badge/lang-English-7A8295)](README.md)
[![Français](https://img.shields.io/badge/lang-Fran%C3%A7ais-1E4FB8)](README.fr.md)

Application Android personnelle pour suivre ses droits de cotisation au CELI
(compte d'épargne libre d'impôt) et au CELIAPP (compte d'épargne libre d'impôt
pour l'achat d'une première propriété).

Elle remplace un classeur Excel où les droits de chaque année étaient saisis à
la main au lieu d'être calculés. Corriger un montant dans le journal des
transactions désynchronisait tout le reste sans avertissement.

## Principe

La base ne stocke aucun résultat de calcul. Elle contient seulement les
saisies : le profil, la table des plafonds annuels et le journal des dépôts et
retraits. L'application recalcule les droits à partir de ces trois entrées à
chaque affichage, et il n'y a pas de table de droits par année.

Le CELI et le CELIAPP ont chacun leur moteur de calcul. Leurs règles diffèrent
sur le début de l'accumulation, la restitution des droits après un retrait, le
plafond à vie et le report des droits inutilisés, alors un moteur commun
paramétré par un drapeau ne tiendrait pas.

Le code, ses commentaires et la documentation technique sont en anglais.
L'application, elle, s'affiche en anglais ou en français selon la langue de
l'appareil.

## État

Le design est dans
[`docs/superpowers/specs/`](docs/superpowers/specs/2026-09-07-tfsa-fhsa-tracker-design.md)
(en anglais).

Déjà livré :

0. Installation du SDK Android en ligne de commande et wrapper Gradle
1. Moteur de calcul (module Kotlin pur) et ses tests
2. Persistance Room, export et import JSON
3. Interface Compose : accueil, détail d'un compte, réglages
4. Journal des transactions : ajout, modification, suppression
5. Plafonds du CELI déjà inscrits, et lecture du plafond de l'année sur le
   site de l'ARC, proposé puis confirmé à la main
6. Sauvegarde : copie automatique vers le compte Google et transfert
   d'appareil, plus export et import d'un fichier JSON depuis les réglages
7. Interface en anglais et en français, thèmes clair et sombre, affichage en
   deux volets sur tablette

À venir :

8. Instantané des droits déclarés par l'ARC
9. APK

## Avertissement

Ce projet ne donne aucun conseil fiscal ou financier. Les montants affichés
sont calculés à partir des données saisies par l'utilisateur et n'ont aucune
valeur officielle. Pour connaître vos droits de cotisation, fiez-vous à Mon
dossier de l'Agence du revenu du Canada.

## Confidentialité

Le dépôt est public et ne contient aucune donnée financière nominative. Les
scénarios de test sont fictifs, et `.gitignore` exclut les bases de données et
les exports JSON.

## Licence

[PolyForm Shield License 1.0.0](LICENSE). L'usage, la modification et la
redistribution sont libres, sauf pour construire un produit qui fait
concurrence à celui du concédant. Cette restriction n'expire pas.
