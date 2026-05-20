# H360 Android - GitHub Signed Release Setup

Ce dossier contient des scripts Windows pour configurer et lancer un build APK/AAB **release signé** via GitHub Actions.

## Prérequis

1. Installer GitHub CLI: https://cli.github.com/
2. Avoir un keystore Android (`.jks` ou `.keystore`)
3. Workflow présent: `.github/workflows/android-release-signed.yml`

## Étapes (A -> Z)

1. Générer le Base64 du keystore:
   - `01_prepare_keystore_base64.bat`
2. Configurer les secrets GitHub:
   - `02_configure_github_secrets.bat`
3. Lancer le workflow release:
   - `03_trigger_github_release_build.bat`

## Secrets GitHub utilisés

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

## Où récupérer les artefacts

Dans GitHub Actions:

- `h360pos-webview-release-apk-signed`
- `h360pos-webview-release-aab-signed`

## Vérification rapide

Après lancement:

1. Ouvrir `https://github.com/<owner>/<repo>/actions`
2. Entrer dans le run `Android Signed Release (AAB + APK)`
3. Vérifier que le job est `green`
4. Télécharger les artifacts

