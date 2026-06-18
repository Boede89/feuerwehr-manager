FROM olbat/cupsd:latest
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3-minimal \
    && rm -rf /var/lib/apt/lists/*
COPY docker/cups/cupsd.conf /etc/cups/cupsd.conf
COPY docker/cups/print-relay.py /print-relay.py
COPY docker/cups/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh /print-relay.py
ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]
