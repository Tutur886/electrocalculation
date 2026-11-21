# ElectroCalc

Application Android d'électronique (Kotlin + Jetpack Compose) avec 14 modules de calcul
et une barre de recherche. Elle contient aussi un espace sécurisé caché qui chiffre des
fichiers (AES-256-GCM) sur une clé USB.

## Compiler l'APK (automatique, sans PC)

Le build se fait sur les serveurs de GitHub. Il suffit de pousser le code sur un dépôt.

### Pousser depuis le téléphone (terminal Linux du S26)

1. Sur github.com (navigateur du tél), crée un dépôt **vide** nommé `electrocalculation`
   (sans README). Crée aussi un jeton : Settings → Developer settings →
   Personal access tokens → Fine-grained token, avec le droit **Contents: Read and write**
   sur ce dépôt. Copie le jeton (`github_pat_…`).
2. Mets `electrocalculation.zip` dans le dossier **Download** du téléphone.
3. Dans le terminal :
   ```bash
   sudo apt install -y git unzip
   cd ~
   unzip -o /mnt/shared/Download/electrocalculation.zip -d electrocalculation
   cd electrocalculation
   git init
   git add .
   git commit -m "ElectroCalc"
   git branch -M main
   git remote add origin https://<TON_JETON>@github.com/Tutur886/electrocalculation.git
   git push -u origin main
   ```
4. Onglet **Actions** du dépôt → le workflow « Build APK » se lance tout seul.
5. Quand il est vert, ouvre-le, télécharge l'artefact **electrocalculation-apk**,
   dézippe → `app-debug.apk`, installe-le (autorise « sources inconnues »).

> Le jeton se retrouve dans l'historique du terminal. Pour l'effacer ensuite :
> `history -c` et supprime la ligne `remote` avec `git remote remove origin` si tu
> ne pousses plus.

## Les 14 modules

Loi d'Ohm · Résistance pour LED · Timer 555 (astable) · Diviseur de tension ·
Résistances série/parallèle · Condensateurs série/parallèle · Filtre RC ·
Réactance (Xc/XL) · Régulateur LM317 · Convertisseur bin/déc/hex · Portes logiques ·
Triphasé · Chute de tension (câble) · Autonomie batterie.

La barre de recherche en haut filtre les modules par mot-clé.

## Espace sécurisé caché

- **Première configuration :** cherche `000` dans la barre. Un écran s'ouvre pour
  définir ton mot de passe secret.
- **Ensuite :** cherche ton mot de passe dans la barre → l'espace sécurisé s'ouvre.
- Dedans : choisis le dossier de la clé USB.
- **Cacher :** copie un fichier sur la clé (via Mes fichiers), puis touche-le dans la
  liste « Fichiers en clair sur la clé ». Il est chiffré en AES-256 sous un nom aléatoire
  `.bin` et **l'original en clair est effacé immédiatement de la clé**.
- **Importer** depuis le téléphone est aussi possible (bouton dédié) ; l'original sur le
  téléphone est effacé si le fournisseur le permet, sinon à faire à la main.
- **Ouvrir :** touche un fichier `.bin`. Il est déchiffré **dans la mémoire privée de
  l'app** (jamais sur la clé) et ouvert dans l'app adaptée. Cette copie en clair est
  effacée dès que tu quittes le coffre (bouton Retour).

Le mot de passe n'est jamais stocké en clair : seule une empreinte PBKDF2 sert à le
reconnaître dans la barre, et la clé de chiffrement est dérivée du mot de passe à
chaque fichier.

## Notes de sécurité (honnêtes)

- C'est de la **vraie cryptographie** (AES-256-GCM, PBKDF2-HMAC-SHA256, 120 000
  itérations). Sans le mot de passe, les `.bin` sont illisibles.
- La robustesse dépend entièrement de la **longueur de ton mot de passe**. Prends une
  phrase, pas un mot court.
- Le camouflage (app d'électronique) arrête un regard occasionnel, pas une analyse
  poussée du téléphone : l'APK contient visiblement le code du coffre.
- Aucun fichier en clair n'est jamais écrit sur la clé. Attention : l'app externe qui
  ouvre un fichier déchiffré (galerie, lecteur…) peut en garder sa propre copie/cache.
- Pensé pour protéger **tes propres fichiers**. Garde une sauvegarde : si tu oublies le
  mot de passe, rien n'est récupérable.

## Personnalisation

- Nom de l'app : `app/src/main/res/values/strings.xml`.
- Icône : aucune icône personnalisée n'est fournie (icône système par défaut).
- Package : `ch.tutur.electrocalc`.
