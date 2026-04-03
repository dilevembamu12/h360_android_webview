# H360 POS Android WebView (MVP)

Application Android WebView pour accéder à H360 POS sans taper l’URL dans un navigateur.

## Fonctionnalités incluses
- Ouverture directe de `https://pos.h360.cd/login`
- Splash screen brandé H360
- Deep links Android (`https://pos.h360.cd/...`, `https://stack.git.h360.cd/...`)
- Overlay maintenance si backend retourne `503`
- Mode kiosk optionnel (flag build)
- Demande permission notifications (Android 13+)
- Gestion cookies/session WebView
- Barre de progression de chargement
- Pull-to-refresh
- Upload de fichiers (input file)
- Téléchargements via `DownloadManager`
- Retour Android intelligent (back webview -> fermeture app)
- Bannière hors ligne

## Prérequis
- Android Studio (Giraffe+)
- JDK 17
- Android SDK installé

## Ouvrir le projet
1. Android Studio -> `Open`
2. Choisir le dossier `H360-Android-WebView`
3. Laisser Gradle sync

## Changer l’URL cible (simple)
Modifier une seule valeur dans:
- `app/build.gradle.kts`

Clé:
- `buildConfigField("String", "WEBVIEW_BASE_URL", "...")`
- `buildConfigField("String", "ALLOWED_INTERNAL_HOSTS", "...")`
- `buildConfigField("String", "MAINTENANCE_CHECK_URL", "...")`
- `buildConfigField("boolean", "ENABLE_KIOSK_MODE", "false|true")`

Exemples:
- `https://pos.h360.cd/login`
- `https://stack.git.h360.cd/login`

## Branding Splash
Le logo splash est ici:
- `app/src/main/res/drawable-nodpi/splash_logo.png`

## Build APK (debug)
Dans Android Studio:
- `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`

Sortie habituelle:
- `app/build/outputs/apk/debug/app-debug.apk`

## Build APK release (signé)
1. `Build` -> `Generate Signed Bundle / APK`
2. Choisir `APK`
3. Utiliser un keystore de production

## Build sans Android Studio (GitHub Actions)
Un workflow CI est fourni:
- `.github/workflows/android-build.yml`

### Déclenchement
1. Push le projet sur GitHub
2. Onglet `Actions`
3. Choisir `Android APK Build`
4. Cliquer `Run workflow`

### Récupération APK
À la fin du job, télécharge les artifacts:
- `h360pos-webview-debug-apk`
- `h360pos-webview-release-apk-unsigned`

### Notes
- Le release généré ici est **unsigned** (non signé).
- Pour Play Store, il faut signer l’APK/AAB avec ton keystore.

## Notes production recommandées
- Mettre `APP_URL` de production dans l’URL WebView
- Ajouter pinning TLS (option avancée)
- Activer Firebase Crashlytics/Analytics
- Prévoir un écran maintenance côté backend pour les pannes

## Limites du MVP
- Pas encore de notifications push
- Pas encore d’écran splash natif brandé
- Pas encore de deep links

