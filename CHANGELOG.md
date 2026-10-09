# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]
- remplacement de l'architecture double `RtspServerCamera2` par deux sources Camera2 coordonnées via l'API Android de capture concurrente.
- vérification explicite de la compatibilité matérielle avant d'activer les flux avant et arrière simultanément.
- ajout d'un mode de repli à une caméra active pour les téléphones ne supportant pas la capture simultanée avant/arrière.
- conservation de deux serveurs RTSP indépendants, sur les ports 8554 et 8555 par défaut.

### Added
- Initial Android application foundation using Kotlin, Jetpack Compose and Material 3.
- Dual RTSP server architecture for simultaneous front and rear camera streams.
- Persistent RTSP configuration with independent ports, resolution, frame rate and bitrate.
- Configurer RTSP command in the application menu.
- Automated unit tests for RTSP configuration validation.
- H.264 video encoding through the Android RTSP Server library.
- Camera2 concurrent-device controller for Android 11+ compatible hardware.

### Fixed
- sélection explicite de la caméra avant lors de la création du flux de repli à caméra unique.
- Suppression d'une redéfinition interdite de `StreamBase.stopPreview()` qui provoquait un plantage au démarrage en mode caméra unique.
- Correction de l'affichage de la caméra avant lors du changement de flux.
- Correction de l'orientation de l'aperçu vidéo en portrait et en paysage.
- Suppression du verrouillage forcé en portrait de l'activité.
- Correction de la compilation de MainActivity avec l'import de l'extension Compose setContent.
