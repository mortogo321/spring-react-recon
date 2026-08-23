# syntax=docker/dockerfile:1.7
#
# Two stages: build the boot jar, then run it on a JRE with nothing else in the image.
#
# The layered extraction in the middle is the part that pays for itself. A boot jar is one fat
# archive, so a one-line application change rewrites the whole ~60 MB layer; extracting Boot's own
# layer index puts dependencies, spring-boot-loader and application classes in separate image
# layers, and only the last one — a few hundred kilobytes — changes on a normal deploy.

# ---------------------------------------------------------------- build
FROM eclipse-temurin:26-jdk AS build
WORKDIR /src

# The wrapper, the build scripts and the version catalog first, on their own: these change rarely,
# so Gradle's dependency resolution stays cached across ordinary source edits.
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY backend/ backend/

# -x test on purpose: tests run in CI against the same commit, and a container build is not the
# place to discover a failure. --no-daemon because the daemon would outlive the build layer.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :backend:recon-api:bootJar -x test

# ---------------------------------------------------------------- layer split
FROM eclipse-temurin:26-jre AS layers
WORKDIR /layers
COPY --from=build /src/backend/recon-api/build/libs/recon-api.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:26-jre AS runtime

# curl for the healthcheck below; nothing else is added to the base image.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl tzdata \
    && rm -rf /var/lib/apt/lists/*

# A fixed uid rather than a name, so a bind-mounted volume has predictable ownership.
RUN groupadd --system --gid 10001 recon \
    && useradd --system --uid 10001 --gid recon --home /app --shell /usr/sbin/nologin recon
WORKDIR /app

# Ordered least- to most-frequently-changed, which is the whole point of the split: third-party
# dependencies move on a release cadence, this application's classes move on every commit.
COPY --from=layers --chown=recon:recon /layers/extracted/dependencies/ ./
COPY --from=layers --chown=recon:recon /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=recon:recon /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=recon:recon /layers/extracted/application/ ./

USER recon:recon
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the container limit is set by whoever runs this, and
# a hard-coded heap either wastes the difference or gets the process OOM-killed.
ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# The readiness group includes both databases and the outbox, so this probe answers "can this
# instance actually reconcile" rather than "is the JVM alive".
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

# The launcher layout, not `-jar`: the fat jar has been unpacked into layers, so JarLauncher is
# what assembles the classpath from BOOT-INF/classpath.idx.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
