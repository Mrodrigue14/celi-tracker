#!/usr/bin/env bash
# Echoue si une valeur personnelle apparait dans un fichier suivi par git.
#
# Le depot est public. Les fixtures y sont synthetiques, mais les vraies valeurs
# ont deja ete reintroduites plusieurs fois par copier-coller depuis un
# document de travail. Ce script rend la verification mecanique.
#
# Les motifs eux-memes sont EXCLUS du depot: les ecrire ici les publierait,
# ce qui serait exactement le probleme qu'on cherche a eviter. Ils vivent dans
# local-data/motifs-prives.txt, un fichier gitignore, une expression reguliere
# etendue par ligne. Sans ce fichier, le script ne verifie rien et le dit.
set -euo pipefail

MOTIFS="local-data/motifs-prives.txt"

if [ ! -f "$MOTIFS" ]; then
    echo "verifier-confidentialite: $MOTIFS absent, aucune verification faite."
    echo "  Ce fichier contient les motifs prives, un par ligne. Il est gitignore."
    exit 0
fi

trouve=0
while IFS= read -r motif; do
    [ -z "$motif" ] && continue
    case "$motif" in \#*) continue ;; esac
    if resultat=$(git grep -nE "$motif" -- ':!local-data' ':!tools/verifier-confidentialite.sh' 2>/dev/null); then
        echo "MOTIF PRIVE TROUVE : $motif"
        echo "$resultat" | sed 's/^/  /'
        trouve=1
    fi
done < "$MOTIFS"

if [ "$trouve" -eq 1 ]; then
    echo
    echo "Des valeurs personnelles sont presentes dans des fichiers suivis par git."
    echo "Le depot est public. Remplace-les par des valeurs synthetiques."
    exit 1
fi

echo "verifier-confidentialite: aucun motif prive dans les fichiers suivis."
