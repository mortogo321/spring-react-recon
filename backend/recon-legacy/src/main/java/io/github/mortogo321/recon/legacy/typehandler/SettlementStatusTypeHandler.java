package io.github.mortogo321.recon.legacy.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import io.github.mortogo321.recon.domain.model.SettlementStatus;

/**
 * The legacy feed stores status as a single {@code CHAR(1)}. Translating it at the boundary keeps
 * the character codes out of the domain, and turns an unexpected code into a loud failure on the
 * row that caused it rather than a silent mis-classification.
 */
@MappedTypes(SettlementStatus.class)
public class SettlementStatusTypeHandler extends BaseTypeHandler<SettlementStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SettlementStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, String.valueOf(parameter.code()));
    }

    @Override
    public SettlementStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public SettlementStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public SettlementStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private SettlementStatus parse(String raw) {
        return raw == null || raw.isBlank() ? null : SettlementStatus.fromCode(raw);
    }
}
