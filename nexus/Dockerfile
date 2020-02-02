FROM sonatype/nexus3:3.20.1

ARG BLOBSTORE_VERSION=0.10.1

ADD --chown=nexus:nexus https://repo1.maven.org/maven2/org/sonatype/nexus/plugins/nexus-blobstore-google-cloud/${BLOBSTORE_VERSION}/nexus-blobstore-google-cloud-${BLOBSTORE_VERSION}-bundle.kar /opt/sonatype/nexus/deploy/
