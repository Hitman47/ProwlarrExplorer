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

## Build

```bash
./gradlew assembleDebug        # APK debug non signé
./gradlew assembleRelease      # signé si keystore.properties existe
```

Signature : `signing/new-keystore.ps1` (clé locale, hors dépôt) puis
`signing/push-secrets.ps1` (secrets GitHub). Le workflow `build-android.yml`
publie l'APK signé sur chaque tag `v*`.
