package org.mypdns.chongluadao_importer;

import java.sql.*;

public class SqlAdapter {

    private final Statement statement;

    public SqlAdapter(String target) {
        Statement tryStatement = null;
        try {
            final var connection = DriverManager.getConnection(target);

            tryStatement = connection.createStatement();
        } catch (SQLException throwables) {
            System.out.println("Error logon DB: " + throwables.getMessage());
            System.exit(2);
        }
        statement = tryStatement;
    }

    public ResultSet query(String queryString) throws SQLException {
        return statement.executeQuery(queryString);
    }

    public void removeOldRecords(String table) throws SQLException {
        statement.execute("DELETE FROM `" + table + "` WHERE `name` LIKE '%.chongluadao.mypdns.cloud' AND `type` NOT LIKE 'SOA' AND `type` NOT LIKE 'NS'");
    }
}
