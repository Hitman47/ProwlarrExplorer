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

## Build

```bash
./gradlew assembleDebug        # APK debug non signé
./gradlew assembleRelease      # signé si keystore.properties existe
```

Signature : `signing/new-keystore.ps1` (clé locale, hors dépôt) puis
`signing/push-secrets.ps1` (secrets GitHub). Le workflow `build-android.yml`
publie l'APK signé sur chaque tag `v*`.
