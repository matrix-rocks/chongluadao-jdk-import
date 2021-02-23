package org.mypdns.chongluadao_importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller {

    private final String sqlTarget;

    private final String targetTable;

    private final String targetTableColumn;

    private final String excludeTable;

    private final String excludeTableColumn;

    private final String upstreamApi;

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Help:").append("\n");
            stringBuilder.append("java -jar chongluadao-importer.jar <target sql url> <target table> <target domain column> <exclude table> <exclude column> <upstream api>").append("\n\n");
            stringBuilder.append("target sql url ---- \"jdbc:<mariadb|mysql>://<domain>:<port>/<database>?user=<user>&password=<password>\"").append("\n");
            stringBuilder.append("target table ------ table where the new entries shall be pushed (will be wiped before!)").append("\n");
            stringBuilder.append("exclude table ----- table which contains domains which should not be in the list of entries in target table").append("\n");
            stringBuilder.append("upstream api ------ url to the data source. GET, body format JSON: [{_id, url, meta{}}]. Example: https://api.chongluadao.vn/v1/blacklist");
            System.out.println(stringBuilder.toString());
            System.exit(0);
        } else {
            new Controller(args[0], args[1], args[2], args[3], args[4], args[5]);
        }
    }

    public Controller(String sqlTarget, String targetTable, String targetTableColumn, String excludeTable, String excludeTableColumn, String upstreamApi) throws IOException {
        this.sqlTarget = sqlTarget;
        this.targetTable = targetTable;
        this.targetTableColumn = targetTableColumn;
        this.excludeTable = excludeTable;
        this.excludeTableColumn = excludeTableColumn;
        this.upstreamApi = upstreamApi;

        //Logon into DB for later
        final var sqlAdapter = new SqlAdapter(this.sqlTarget);
        System.out.println("Successfully logged on at DB");

        //Get current list
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(this.upstreamApi)
                .build();

        ResponseBody responseBody = client.newCall(request).execute().body();


        //Map response into internal object model
        final var objectMapper = new ObjectMapper();
        if (responseBody == null) {
            System.out.println("Body is null");
            System.exit(1);
        }
        var upstreamEntries = objectMapper.readValue(responseBody.string(), new TypeReference<HashSet<UpstreamEntry>>(){});

        if (upstreamEntries.size() == 0) {
            System.out.println("Error upstream returned no elements");
            System.exit(1);
        }
        System.out.println("Got " + upstreamEntries.size() + " elements from upstream");

        //Extract domains from internal object model which contains whole URIs
        final var upstreamDomains = new HashSet<String>();
        int ignoredEntries;
        upstreamEntries.forEach(upstreamEntry ->
            {
                try {
                    var uri = URI.create(upstreamEntry.url);
                    if (!upstreamDomains.add(uri.getHost())) {
                        System.out.println("Warning domain \"" + uri.getHost() + "\" is already in the list (URL: \"" + upstreamEntry.url + "\")");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Error parsing domain: " + e.getMessage());
                }
            }
        );
        System.out.println("Parsed " + upstreamDomains.size() + " domains from upstream");

        //Pull list from DB with domains which are to be removed from the upstream list
        final var databaseDomains = new HashSet<String>();
        try {
            var result = sqlAdapter.query("SELECT `" + this.excludeTableColumn + "` FROM `" + this.excludeTable + "` WHERE `" + this.excludeTableColumn + "` REGEXP '.*\\.adult\\.mypdns\\.cloud'");

            //final Pattern pattern = Pattern.compile("(\\*\\.)?(.+(?<!\\.rpz))(\\.rpz)?\\.mypdns\\.cloud");
            final Pattern pattern = Pattern.compile("(\\*\\.)?(.+)(\\.adult)\\.mypdns\\.cloud");

            while (result.next()) {
                final var currentDomain = result.getString(this.excludeTableColumn);
                //Remove ".rpz.mypdns.cloud"
                Matcher matcher = pattern.matcher(currentDomain);
                if (matcher.find()) {
                    databaseDomains.add(matcher.replaceAll("$2"));
                } else {
                    databaseDomains.add(result.getString(this.excludeTableColumn));
                }
            }
            System.out.println("Selected " + databaseDomains.size() + " domains from table \"" + this.excludeTable + "\" which are to be removed from the upstream list if exist");
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            System.exit(3);
        }

        //Remove all domains which are in the by the database HashSet
        final var sizeBefore = upstreamDomains.size();
        upstreamDomains.removeAll(databaseDomains);
        System.out.println("Removed " + (sizeBefore - upstreamDomains.size()) + " domains (" + upstreamDomains.size() + " left)");

        //Insert new list after clearing the old table
        try {
            //Build values string for insertion
            StringBuilder stringBuilder = new StringBuilder();
            upstreamDomains.forEach(domain -> {
                stringBuilder.append("('").append(domain).append("'), ");
            });
            var values = stringBuilder.toString();
            //remove the last comma
            values = values.substring(0, values.length() - 2);

            sqlAdapter.clearTable(this.targetTable);
            System.out.println("Cleared the table \"" + this.targetTable + "\"");
            sqlAdapter.insertInto(this.targetTable, new String[]{this.targetTableColumn}, values);
            System.out.println("Inserted new domain list into \"" + this.targetTable + "\"");
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            System.exit(4);
        }

        System.out.println("Bye!");
    }
}
