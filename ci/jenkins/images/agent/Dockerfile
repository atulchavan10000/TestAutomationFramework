FROM jenkins/inbound-agent:3391.va_37fa_a_305d6d-3-jdk21
USER root
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        git \
        unzip \
    && rm -rf /var/lib/apt/lists/*

USER jenkins