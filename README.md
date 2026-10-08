# GPT-Android-Miroir

Application Android standalone développée en Kotlin avec Jetpack Compose et un pipeline Camera2/RTSP.

## Fonctionnalités actuelles

- Affichage vidéo en temps réel de la caméra arrière au démarrage.
- Menu vertical `⋮` en haut à droite.
- Choix de la caméra avant ou arrière sans quitter l'application.
- Serveur RTSP intégré avec deux flux indépendants avant/arrière.
- Configuration persistante des ports RTSP, de la résolution, du nombre d'images par seconde et du débit.
- Commande `Configurer RTSP` dans le menu.
- Demande de permission caméra lors du premier lancement.

### Adresses RTSP par défaut

- Caméra arrière : `rtsp://ADRESSE_IP_DU_TELEPHONE:8554/`
- Caméra avant : `rtsp://ADRESSE_IP_DU_TELEPHONE:8555/`

Les deux flux utilisent H.264. La possibilité d'utiliser simultanément les deux caméras dépend des capacités matérielles du téléphone.

## Tests

Les tests unitaires couvrent notamment la validation des ports, l'activation des flux et les paramètres vidéo RTSP.

## Développement

Le projet suit un workflow Git par branche dédiée, avec validation CI et génération d'un APK debug avant fusion vers `main`.

Branche de développement : `feature/camera-preview`
