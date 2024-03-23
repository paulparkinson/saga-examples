#!/bin/bash

sqlplus="sql sys/knl_test7@0.0.0.0:1521 as sysdba @./sql-scripts/setupPDBs.sql"

echo quit | $sqlplus

sqlplusCloudBank="sql admin/test@0.0.0.0:1521/CDB1_PDB1 @./sql-scripts/cloudbank.sql"

echo quit | $sqlplusCloudBank

sqlplusBankA="sql admin/test@0.0.0.0:1521/CDB1_PDB2 @./sql-scripts/bankA.sql"

echo quit | $sqlplusBankA

sqlplusBankB="sql admin/test@0.0.0.0:1521/CDB1_PDB3 @./sql-scripts/bankB.sql"

echo quit | $sqlplusBankB

sqlplusCreditScore="sql admin/test@0.0.0.0:1521/CDB1_PDB4  @./sql-scripts/creditscore.sql"

echo quit | $sqlplusCreditScore
