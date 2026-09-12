# Prowlarr Explorer

Client Android (Kotlin / Compose) pour Prowlarr : recherche multi-indexers et
envoi d'une release vers le client de téléchargement configuré dans Prowlarr.

## v0.1

- Réglages : URL + clé API (chiffrée dans le Keystore Android), bouton Tester.
- Recherche : texte, filtre catégorie (Tout / Films / Séries / Musique / Livres),
  filtre indexers.
- Résultats triés par seeders ; fiche avec taille, S/L, âge, indexer.
- Envoi : `POST /api/v1/search {guid, indexerId}` — Prowlarr pousse vers son
  client de téléchargement (qBittorrent, SAB…). Aucune config qBittorrent côté app.

## v0.2

- Réglages : schéma http/https + IP + port pour chaque service.
- qBittorrent : login/mot de passe ou bypass IP ; onglet Téléchargements (progression,
  vitesse, ETA, pause / reprise / suppression), compatible 4.x et 5.x.
- Mise à jour dans l'app : vérification des releases GitHub (au plus une fois par 6 h
  ou via Réglages), téléchargement de l'APK et ouverture de l'installeur. Tant que le
  dépôt est privé, un token GitHub fine-grained (Contents : Read) est requis.

## v0.3

- Recherche : titre lisible (année, saison, chips qualité/langue), tri Seeders / Taille / Date,
  masquage des 0 seed, historique, glisser vers la droite = envoyer, snackbar « Voir ».
- « Prowlarr Explorer » dans le menu de sélection de texte et le partage Android.
- Téléchargements : sections Actifs / En pause / Terminés, badge d'actifs, débit dans l'onglet,
  glisser = pause/reprise, tirer pour rafraîchir.
- Réglages : accueil avec état des services, test automatique à la saisie, œil/coller sur les
  secrets, assistant au premier lancement, thème Clair / Sombre / Système.
- Tablette en paysage : rail de navigation + fiche dans un volet droit.

## v0.4

- qBittorrent : clé API (≥ 5.2) en priorité, sinon login/mot de passe, sinon bypass IP.
- Envoi direct à qBittorrent (magnet, ou .torrent récupéré via Prowlarr puis poussé en
  multipart) avec choix de la catégorie, mémorisée pour les envois suivants ; repli sur le
  grab Prowlarr pour l'usenet ou sans qBittorrent. Un seul bouton.

## v0.5

- Notification « téléchargement terminé » (WorkManager, 15 min, désactivable dans Réglages).
- Ajout manuel : bouton « + » (coller un magnet / lien .torrent), partage ou « Ouvrir avec »
  d'un lien magnet vers l'app → qBittorrent avec catégorie.
- Résultats identiques sur plusieurs indexers regroupés (« indexer +2 », liste « Aussi sur »
  dans la fiche).
- Navigateur intégré pour la page indexer : cookies persistants, cookie Prowlarr injecté
  quand l'indexer en a un, liens magnet / .torrent cliqués envoyés à qBittorrent.

- Fiche torrent : fichiers (à télécharger ou non), catégorie, revérification, limites de vitesse.
- Journal des envois (icône horloge dans Téléchargements) ; une entrée encore présente dans
  qBittorrent ouvre sa fiche.

## Build

```bash
./gradlew assembleDebug        # APK debug non signé
./gradlew assembleRelease      # signé si keystore.properties existe
```

Signature : `signing/new-keystore.ps1` (clé locale, hors dépôt) puis
`signing/push-secrets.ps1` (secrets GitHub). Le workflow `build-android.yml`
publie l'APK signé sur chaque tag `v*`.
