#!/bin/bash
set -e
for db in ticketing_auth ticketing_event ticketing_booking ticketing_payment ticketing_notification; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "CREATE DATABASE $db;"
done
