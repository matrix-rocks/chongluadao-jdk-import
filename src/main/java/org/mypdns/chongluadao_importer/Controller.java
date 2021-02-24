package org.mypdns.chongluadao_importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.mypdns.powerdns.model.zone.*;
import org.mypdns.powerdns.model.zone.Record;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller {

    private final String sqlTarget;

    private final String targetApiEndpoint;

    private final String apiKey;

    private final String table;

    private final String excludeTableColumn;

    private final String upstreamApi;

    private final SqlAdapter sqlAdapter;

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Help:").append("\n");
            stringBuilder.append("java -jar chongluadao-importer.jar <local sql url> <local api endpoint> <local api key> <local table> <upstream api>").append("\n\n");
            stringBuilder.append("local sql url ----------- \"jdbc:<mariadb|mysql>://<domain>:<port>/<database>?user=<user>&password=<password>\"").append("\n");
            stringBuilder.append("local api endpoint ------ table where the new entries shall be pushed (will be wiped before!)").append("\n");
            stringBuilder.append("local table ------------- table which is used for working on").append("\n");
            stringBuilder.append("upstream api ------------ url to the data source. GET, body format JSON: [{_id, url, meta{}}]. Example: https://api.chongluadao.vn/v1/blacklist");
            System.out.println(stringBuilder.toString());
            System.exit(0);
        } else {
            new Controller(args[0], args[1], args[2], args[3], args[4], args[5]);
        }
    }

    public Controller(String sqlTarget, String targetApiEndpoint, String apiKey, String table, String excludeTableColumn, String upstreamApi) throws IOException {
        this.sqlTarget = sqlTarget;
        this.targetApiEndpoint = targetApiEndpoint;
        this.apiKey = apiKey;
        this.table = table;
        this.excludeTableColumn = excludeTableColumn;
        this.upstreamApi = upstreamApi;

        //Logon into DB for later
        this.sqlAdapter = new SqlAdapter(this.sqlTarget);
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
                    final var uri = URI.create(upstreamEntry.url);
                    final var domain = uri.getHost();
                    if (!upstreamDomains.add(domain)) System.out.println("Warning domain \"" + domain + "\" is already in the list (URL: \"" + upstreamEntry.url + "\")");
                    //Add also www subdomains
                    if (!domain.startsWith("www.")) upstreamDomains.add("www." + domain);
                    //Add also second level domains
                    if (domain.startsWith("www.")) upstreamDomains.add(domain.substring(4));
                } catch (IllegalArgumentException e) {
                    System.out.println("Error parsing domain: " + e.getMessage());
                }
            }
        );
        //Divide by two as they're duplicated with or without www. only for comparison
        System.out.println("Parsed " + (upstreamDomains.size() / 2) + " domains from upstream");

        //Remove all domains which are in the database HashSet
        final var sizeBefore = upstreamDomains.size();
        upstreamDomains.removeAll(getExcludeDomains());
        //Divide by two as they're duplicated with or without www. only for comparison
        System.out.println("Removed " + (sizeBefore - upstreamDomains.size()) + " domains (" + (upstreamDomains.size() / 2) + " left to be pushed)");

        //Send new domains to the auth server
        request = new Request.Builder()
                .url(targetApiEndpoint)
                .method("PATCH", RequestBody.create(buildNewRrset(upstreamDomains)))
                .header("X-API-Key", apiKey)
                .build();

        try {
            sqlAdapter.removeOldRecords(this.table, upstreamDomains);
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("Error clearing the old records in the target table");
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            System.exit(3);
        }

        final Response response = client.newCall(request).execute();
        if (response.code() != 204) {
            System.out.println("Error while pushing new entries in auth server: Status code " + response.code() + ", body:" + response.body().string());
        } else {
            System.out.println("Pushed successfully");
        }

        System.out.println("Bye!");
    }

    @NotNull
    private HashSet<String> getExcludeDomains() {
        //Pull list from DB with domains which are to be removed from the upstream list
        final var excludeDomains = new HashSet<String>();
        try {
            var result = sqlAdapter.query("SELECT `name` FROM `" + this.table + "` WHERE `" + this.excludeTableColumn + "` REGEXP '.*\\.adult\\.mypdns\\.cloud'");

            //final Pattern pattern = Pattern.compile("(\\*\\.)?(.+(?<!\\.rpz))(\\.rpz)?\\.mypdns\\.cloud");
            final Pattern pattern = Pattern.compile("(\\*\\.)?(.+)(\\.adult)\\.mypdns\\.cloud");

            while (result.next()) {
                final var currentDomain = result.getString("name");
                //Remove ".rpz.mypdns.cloud"
                Matcher matcher = pattern.matcher(currentDomain);
                if (matcher.find()) {
                    excludeDomains.add(matcher.replaceAll("$2"));
                } else {
                    excludeDomains.add(result.getString("name"));
                }
            }
            System.out.println("Selected " + excludeDomains.size() + " domains from table \"" + this.table + "\" which are to be removed from the upstream list if exist");
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            System.exit(2);
        }
        return excludeDomains;
    }

    private byte[] buildNewRrset(@NotNull HashSet<String> domains) throws JsonProcessingException {
        final var rrsets = new ArrayList<Rrset>();
        //Clear old entries
        rrsets.add(new Rrset(
                ("*.chongluadao.mypdns.cloud."),
                Type.CNAME,
                0,
                Changetype.DELETE,
                null)
        );

        domains.forEach(domain -> {
            if (!domain.startsWith("www.")) {
                rrsets.add(new Rrset(
                    ("*." + domain + ".chongluadao.mypdns.cloud."),
                    Type.CNAME,
                    86400L,
                    Changetype.REPLACE,
                    new Record[]{
                            new Record(".", false)
                    })
                );
            }
        });

        final var objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        return objectWriter.withRootName("rrsets").writeValueAsBytes(rrsets);
    }
}
