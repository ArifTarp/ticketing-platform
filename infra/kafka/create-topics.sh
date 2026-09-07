#!/bin/sh
# Bootstraps the three application topics from root CLAUDE.md's Kafka topics table. Run once the
# broker is healthy. Single-broker demo cluster: replication-factor 1. Partition counts are kept
# small but non-trivial (3) so per-bookingId ordering (message key = bookingId) still works
# correctly — Kafka only guarantees order within a partition, and every message for a given
# bookingId always hashes to the same partition, so 3 partitions is safe and gives a little
# parallelism headroom without needing to reason about cross-partition ordering.
set -e

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"
PARTITIONS="${PARTITIONS:-3}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-1}"

TOPICS="payment.commands payment.events booking.events"

for topic in $TOPICS; do
  echo "Creating topic: $topic"
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION_FACTOR"
done

echo "Topics after bootstrap:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
