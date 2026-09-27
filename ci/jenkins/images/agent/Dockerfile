RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        git \
        unzip \
    && rm -rf /var/lib/apt/lists/*

# Gradle runs as the jenkins user and needs to write downloaded
# distributions, dependency caches, and lock files here.
RUN mkdir -p /home/jenkins/.gradle \
    && chown -R jenkins:jenkins /home/jenkins/.gradle

USER jenkins