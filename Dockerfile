FROM gradle:8.11.1-jdk17

USER root

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl php-cli unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" /tmp/android-cli \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/android-cli.zip \
    && unzip -q /tmp/android-cli.zip -d /tmp/android-cli \
    && mv /tmp/android-cli/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
    && yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null \
    && sdkmanager --sdk_root="$ANDROID_HOME" \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
    && chown -R gradle:gradle "$ANDROID_HOME" \
    && rm -rf /tmp/android-cli /tmp/android-cli.zip \
    && mkdir -p /home/gradle/.android \
    && chown -R gradle:gradle /home/gradle/.android

USER gradle
WORKDIR /workspace
