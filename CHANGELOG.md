# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]
- activation explicite de l'exposition automatique (AE) pour les captures Camera2 des caméras avant et arrière, y compris après application du zoom.
- ajout du zoom par pincement à deux doigts, appliqué à la capture pour l'aperçu et le flux RTSP, en portrait comme en paysage.
- ajout du titre « Miroir Android » en haut de la fenêtre principale et d'une nouvelle icône adaptative évoquant le téléphone, la caméra et le miroir.
- adoption de SemVer et des Conventional Commits pour déterminer automatiquement le niveau de version publié.
- remplacement de l'architecture double `RtspServerCamera2` par deux sources Camera2 coordonnées via l'API Android de capture concurrente.
- vérification explicite de la compatibilité matérielle avant d'activer les flux avant et arrière simultanément.
- ajout d'un mode de repli à une caméra active pour les téléphones ne supportant pas la capture simultanée avant/arrière.
- conservation de deux serveurs RTSP indépendants, sur les ports 8554 et 8555 par défaut.

### Added
- automatisation des publications Android après mise à jour de `main`, avec tag Git, notes de version et APK joint à la Release GitHub.
- Initial Android application foundation using Kotlin, Jetpack Compose and Material 3.
- Dual RTSP server architecture for simultaneous front and rear camera streams.
- Persistent RTSP configuration with independent ports, resolution, frame rate and bitrate.
- Configurer RTSP command in the application menu.
- Automated unit tests for RTSP configuration validation.
- H.264 video encoding through the Android RTSP Server library.
- Camera2 concurrent-device controller for Android 11+ compatible hardware.

### Fixed
- conservation de la caméra avant/arrière sélectionnée lors d'une rotation du téléphone et de la recréation de l'activité.
- adaptation de la rotation vidéo à l'orientation portrait/paysage et conservation des proportions de l'aperçu.
- sélection explicite de la caméra avant lors de la création du flux de repli à caméra unique.
- Suppression d'une redéfinition interdite de `StreamBase.stopPreview()` qui provoquait un plantage au démarrage en mode caméra unique.
- Correction de l'affichage de la caméra avant lors du changement de flux.
- Correction de l'orientation de l'aperçu vidéo en portrait et en paysage.
- Suppression du verrouillage forcé en portrait de l'activité.
- Correction de la compilation de MainActivity avec l'import de l'extension Compose setContent.
