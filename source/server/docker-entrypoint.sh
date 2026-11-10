#!/bin/sh
set -eu
# Render secret files are text; optionally decode a base64 PKCS12 at startup.
if [ -n "${DB_TRUSTSTORE_BASE64_PATH:-}" ]; then
    umask 077
    store_dir=$(mktemp -d /tmp/randomconnect-tls.XXXXXX)
    base64 -d "$DB_TRUSTSTORE_BASE64_PATH" > "$store_dir/aiven.p12"
    export DB_TRUSTSTORE_URL="file:$store_dir/aiven.p12"
fi
exec java -jar /app/app.jar
