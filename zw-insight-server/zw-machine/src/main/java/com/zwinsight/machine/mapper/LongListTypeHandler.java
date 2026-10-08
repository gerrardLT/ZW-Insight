package com.zwinsight.machine.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;
import java.util.List;

/** Explicit Long element type, including small JSON integer IDs. */
public class LongListTypeHandler extends BaseTypeHandler<List<Long>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public void setNonNullParameter(PreparedStatement statement, int index, List<Long> value, JdbcType type) throws SQLException {
        try { statement.setString(index, JSON.writeValueAsString(value)); }
        catch (java.io.IOException e) { throw new SQLException("Invalid work log IDs", e); }
    }
    private List<Long> parse(String json) throws SQLException {
        if (json == null) return null;
        try { return JSON.readValue(json, new TypeReference<List<Long>>() {}); }
        catch (java.io.IOException e) { throw new SQLException("Invalid work log IDs", e); }
    }
    @Override public List<Long> getNullableResult(ResultSet result, String name) throws SQLException { return parse(result.getString(name)); }
    @Override public List<Long> getNullableResult(ResultSet result, int index) throws SQLException { return parse(result.getString(index)); }
    @Override public List<Long> getNullableResult(CallableStatement statement, int index) throws SQLException { return parse(statement.getString(index)); }
}
