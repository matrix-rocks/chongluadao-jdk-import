# Chongluadao importer
This is a small and fast built importing tool to https://api.chongluadao.vn.
It gets the current list of URLs by the external project and compares them with already blocked domains of the category "adult" (pulled from DBMS).
After this, it pushes the cleaned domains into a predefined database table.

The connector's argument interface IS NOT inject-save and does not escape anything in the arguments!

## Setup and first run
The connector accepts parameters via command line arguments.

### Command line arguments
```
java -jar chongluadao-importer.jar <local sql url> <local api endpoint> <local api key> <local table> <upstream api>
local sql url ----------- "jdbc:<mariadb|mysql>://<domain>:<port>/<database>?user=<user>&password=<password>"
local api endpoint ------ table where the new entries shall be pushed (will be wiped before!)
local table ------------- table which is used for working on
upstream api ------------ url to the data source. GET, body format JSON: [{_id, url, meta{}}]. Example: https://api.chongluadao.vn/v1/blacklist
```

When arguments are missing, the help above is shown.

### Exit codes
- 0 okay or arguments missing (help was shown)
- 1 upstream returned invalid data
- 2 failed to pull excluded data from local table
- 3 failed to remove old records in local table

### Build
For building jdk15 is needed (you can try to change the compile version in the pom.xml), maven is needed and both must be in the path variable. Then you should be able to run the command `mvn clean compile assembly:single --file pom.xml` to build the file. The current directory must be the root of the github-repo so that maven finds the pom.xml.
