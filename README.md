# Miroir Android

Application Android autonome développée en Kotlin avec Jetpack Compose et Camera2/RTSP, pour prévisualiser une caméra et diffuser la vidéo via RTSP.

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

## Intégration continue et publications

- Chaque pull request vers `main` déclenche les tests unitaires et la compilation de vérification.
- Chaque mise à jour de `main` déclenche l'analyse des commits depuis la dernière release.
- Les commits suivent la convention [Conventional Commits](https://www.conventionalcommits.org/) : `feat:` pour une fonctionnalité, `fix:` pour une correction, `perf:` pour une amélioration de performance, et `!` ou `BREAKING CHANGE:` pour une rupture de compatibilité.
- Le versionnement suit [Semantic Versioning (SemVer)](https://semver.org/lang/fr/) : rupture de compatibilité → MAJOR, `feat` → MINOR, `fix`/`perf` → PATCH. Les commits `docs`, `test`, `ci`, `build` ou `chore` seuls ne déclenchent pas de nouvelle release.
- Si les tests et la compilation réussissent, GitHub Actions crée le tag Git et la Release avec notes générées automatiquement et APK téléchargeable.
- La première version stable publiée est `v1.0.0`; les prochaines versions sont calculées selon les commits conventionnels depuis ce tag.
- Le nom de version Android et le `versionCode` sont injectés par le workflow de publication ; les compilations locales conservent les valeurs par défaut du projet.

Le développement se fait dans une branche dédiée. La fusion vers `main` reste manuelle après validation.
