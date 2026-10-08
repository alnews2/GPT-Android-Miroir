# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]
- correction de la sélection explicite de la caméra avant/arrière pour les flux RTSP et affichage d'une erreur si le flux sélectionné n'est pas disponible.

### Added
- Initial Android application foundation using Kotlin, Jetpack Compose and Material 3.
- Dual RTSP server architecture for simultaneous front and rear camera streams.
- Persistent RTSP configuration with independent ports, resolution, frame rate and bitrate.
- Configurer RTSP command in the application menu.
- Automated unit tests for RTSP configuration validation.
- H.264 video encoding through the Android RTSP Server library.

### Fixed
- Correction de l'affichage de la caméra avant lors du changement de flux.
- Correction de l'orientation de l'aperçu vidéo en portrait et en paysage.
- Suppression du verrouillage forcé en portrait de l'activité.
- Correction de la compilation de MainActivity avec l'import de l'extension Compose setContent.
