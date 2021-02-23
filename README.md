# Chongluadao importer
This is a small and fast built importing tool to https://api.chongluadao.vn.
It gets the current list of URLs by the external project and compares them with already blocked domains of the category "adult" (pulled from DBMS).
After this, it pushes the cleaned domains into a predefined database table.

The connector's argument interface IS NOT inject-save and does not escape anything in the arguments!

## Setup and first run
The connector accepts parameters via command line arguments.

### Command line arguments
Environment variables can be overwritten with command line arguments:
```
java -jar chongluadao-importer.jar <target sql url> <target table> <target domain column> <exclude table> <exclude column> <upstream api>
target sql url ---- "jdbc:<mariadb|mysql>://<domain>:<port>/<database>?user=<user>&password=<password>"
target table ------ table where the new entries shall be pushed (will be wiped before!)
exclude table ----- table which contains domains which should not be in the list of entries in target table
upstream api ------ url to the data source. GET, body format JSON: [{_id, url, meta{}}]. Example: https://api.chongluadao.vn/v1/blacklist
```

When arguments are missing, the help above is shown.

### Build
for building jdk15 is needed (you can try to change the compile version in the pom.xml), maven is needed and both must be in the path variable. Then you should be able to run the command `mvn clean compile assembly:single --file pom.xml` to build the file. The current directory must be the root of the github-repo so that maven finds the pom.xml.
