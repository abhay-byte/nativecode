# Gradle Standardization Policy

To prevent version sprawl and ensure maximum compatibility across all Java and Android projects, standardize on **Gradle 8.14** (or Gradle 9.4+ if all repositories are modern).

## Standard Version Recommendation
- **Standard Version:** Gradle 8.14
- **Java Compatibility:** Fully supports JDK 17 and JDK 21 (industry standards for Android and Spring Boot).
- **Android Compatibility:** Supports modern Android Gradle Plugins (AGP 8.x) up through `compileSdk = 36`.
- **Plugin Stability:** Maintains backwards compatibility while giving top-tier build speeds without removing deprecated build APIs needed by third-party plugins.

> [!TIP]
> If all projects are brand new and using Android Gradle Plugin 9.x, standardize on Gradle 9.4.1 instead.

## How to Upgrade
Whenever opening a project or wanting an agent to work on it, run this single command inside the project root to update its wrapper to the unified version:

```bash
./gradlew wrapper --gradle-version 8.14 --distribution-type all
```

Once updated, commit the modified `gradle/wrapper/gradle-wrapper.properties` file so that every build shares the exact same cached Gradle distribution.

## Reclaiming Disk Space
To clean up old cached distributions and free disk space:

```bash
# 1. Stop any running daemons
pkill -f GradleDaemon || true

# 2. Wipe out old daemon logs and wrapper distributions
rm -rf ~/.gradle/daemon/*
rm -rf ~/.gradle/wrapper/dists/*
```
