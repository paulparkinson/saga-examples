#!/bin/bash




sqlplusClean="sql sys/knl_test7@0.0.0.0:1521 as sysdba  @./sql-scripts/cleanup.sql"

echo quit | $sqlplusClean