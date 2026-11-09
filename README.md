# Walker Chatbot — APK Android

Discute avec Pollinations (gratuit, sans clé ni inscription), depuis une appli Android.

## Comment ça marche

1. **Sans clé** : l'appli appelle `GET https://text.pollinations.ai/<question>`.
   Limite gratuite : 1 question toutes les 30 secondes.
2. **Avec clé** : touche « 🔑 Ma clé Pollinations », colle ta clé et touche OK.
   La clé part en en-tête `Authorization: Bearer <clé>` et lève la limite de débit.

## La clé : laquelle, et où la trouver

- **Bonne clé : la clé publiable, qui commence par `pk_`**.
  Crée-la sur https://enter.pollinations.ai (connexion avec GitHub),
  dans la section des clés API. Elle est faite pour les applis : elle reste
  sur ton téléphone, dans les réglages de l'appli, et n'est jamais envoyée
  ailleurs que chez Pollinations.
- **Mauvaise clé : la clé secrète, qui commence par `sk_`**.
  Elle donne accès à tout ton compte Pollinations (solde Pollen, autres clés).
  Ne la mets JAMAIS dans une appli, et ne la colle JAMAIS dans un chat :
  l'appli la refuse et affiche un avertissement.
- Sans clé, l'appli marche quand même, juste plus lentement.

## Compiler

Le build se fait sur GitHub Actions (`.github/workflows/build.yml`) :
`gradle assembleDebug` → APK dans `app/build/outputs/apk/debug/app-debug.apk`.

- Package : `com.walker.chatbot`, minSdk 26, targetSdk 34.
- Aucune dépendance externe : `HttpURLConnection` uniquement.
