package org.mypdns.chongluadao_importer;

import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.HashSet;

public class SqlAdapter {

    private final Statement statement;

    public SqlAdapter(String target) {
        Statement tryStatement = null;
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(target);
            //conn = DriverManager.getConnection("jdbc:mariadb://10.1.3.12:3306/mypdns?user=mypdns&password=123456");
            //DriverManager.getConnection("jdbc:mysql://10.1.3.12/test?" +
            //        "user=mypdns&password=123456");

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

    public boolean clearTable(String table) throws SQLException {
        return statement.execute("DELETE FROM `" + table + "`");
    }

    public int insertInto(@NotNull String table, @NotNull String[] columns, @NotNull String values) throws SQLException {
        final var stringBuilder = new StringBuilder();
        for (var i = 0; i<columns.length; i++) {
            if (i<columns.length-1) {
                stringBuilder.append(columns[i]).append(", ");
            } else {
                stringBuilder.append(columns[i]);
            }
        }
        return statement.executeUpdate("INSERT INTO `" + table + "` (" + stringBuilder.toString() + ") VALUES " + values);
    }

    public void removeOldRecords(String table) throws SQLException {
        statement.execute("DELETE FROM `" + table + "` WHERE `name` LIKE '%.chongluadao.mypdns.cloud' AND `type` NOT LIKE 'SOA' AND `type` NOT LIKE 'NS'");
    }
}
