#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
ARG JAVA_IMAGE=eclipse-temurin:17-jammy
FROM ${JAVA_IMAGE}

ARG spark_uid=185

RUN groupadd --system --gid=${spark_uid} spark && \
    useradd --system --uid=${spark_uid} --gid=spark -d /nonexistent spark

RUN set -ex; \
    apt-get update; \
    apt-get install -y gnupg2 wget bash tini libc6 libpam-modules krb5-user libnss3 procps net-tools gosu libnss-wrapper; \
    mkdir -p /opt/spark; \
    mkdir /opt/spark/python; \
    mkdir -p /opt/spark/examples; \
    mkdir -p /opt/spark/work-dir; \
    chmod g+w /opt/spark/work-dir; \
    touch /opt/spark/RELEASE; \
    chown -R spark:spark /opt/spark; \
    echo "auth required pam_wheel.so use_uid" >> /etc/pam.d/su; \
    rm -rf /var/lib/apt/lists/*

ARG SPARK_TGZ_URL
ARG SPARK_TGZ_ASC_URL
ARG GPG_KEY=
ARG GPG_KEYS_URL=https://archive.apache.org/dist/spark/KEYS
ARG STRIP_SPARK_JARS=false

RUN set -ex; \
    export SPARK_TMP="$(mktemp -d)"; \
    cd "$SPARK_TMP"; \
    wget -nv -O spark.tgz "$SPARK_TGZ_URL"; \
    wget -nv -O spark.tgz.asc "$SPARK_TGZ_ASC_URL"; \
    export GNUPGHOME="$(mktemp -d)"; \
    if [ -n "$GPG_KEY" ]; then \
      gpg --batch --keyserver hkps://keys.openpgp.org --recv-key "$GPG_KEY" || \
      gpg --batch --keyserver hkps://keyserver.ubuntu.com --recv-keys "$GPG_KEY"; \
    else \
      wget -nv -O KEYS "$GPG_KEYS_URL"; \
      gpg --batch --import KEYS; \
    fi; \
    gpg --batch --verify spark.tgz.asc spark.tgz; \
    gpgconf --kill all; \
    rm -rf "$GNUPGHOME" spark.tgz.asc; \
    tar -xf spark.tgz --strip-components=1; \
    chown -R spark:spark .; \
    mv jars /opt/spark/; \
    if [ "$STRIP_SPARK_JARS" = "true" ]; then \
      find /opt/spark/jars -mindepth 1 -maxdepth 1 -type f -delete; \
    fi; \
    mv RELEASE /opt/spark/; \
    mv bin /opt/spark/; \
    mv sbin /opt/spark/; \
    mv kubernetes/dockerfiles/spark/decom.sh /opt/; \
    mv examples /opt/spark/; \
    ln -s "$(basename /opt/spark/examples/jars/spark-examples_*.jar)" /opt/spark/examples/jars/spark-examples.jar; \
    mv kubernetes/tests /opt/spark/; \
    mv data /opt/spark/; \
    mv python/pyspark /opt/spark/python/pyspark/; \
    mv python/lib /opt/spark/python/lib/; \
    mv R /opt/spark/; \
    chmod a+x /opt/decom.sh; \
    cd ..; \
    rm -rf "$SPARK_TMP"

COPY entrypoint.sh /opt/
RUN chmod a+x /opt/entrypoint.sh

ENV SPARK_HOME=/opt/spark

WORKDIR /opt/spark/work-dir

USER spark

ENTRYPOINT [ "/opt/entrypoint.sh" ]
