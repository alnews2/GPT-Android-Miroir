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
- Capture simultanée avant/arrière basée sur l'API Camera2 concurrente d'Android 11+.

### Adresses RTSP par défaut

- Caméra arrière : `rtsp://ADRESSE_IP_DU_TELEPHONE:8554/`
- Caméra avant : `rtsp://ADRESSE_IP_DU_TELEPHONE:8555/`

Les deux flux utilisent H.264. La capture simultanée des caméras avant et arrière n'est disponible que sur les appareils dont le matériel Android déclare cette combinaison comme concurrente. Sur un appareil incompatible, l'application signale explicitement l'indisponibilité au lieu de laisser un seul flux fonctionner silencieusement.

## Tests

Les tests unitaires couvrent notamment la validation des ports, l'activation des flux et les paramètres vidéo RTSP.

## Développement

Le projet suit un workflow Git par branche dédiée, avec validation CI et génération d'un APK debug avant fusion vers `main`.

Branche de développement : `feature/camera-preview`
