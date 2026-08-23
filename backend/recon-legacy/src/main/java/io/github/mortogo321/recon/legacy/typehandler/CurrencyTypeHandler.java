package io.github.mortogo321.recon.legacy.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** Maps Oracle {@code VARCHAR2(3)} ISO codes to {@link Currency} so DTOs stay strongly typed. */
@MappedTypes(Currency.class)
public class CurrencyTypeHandler extends BaseTypeHandler<Currency> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Currency parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getCurrencyCode());
    }

    @Override
    public Currency getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Currency getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Currency getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Currency parse(String raw) {
        return raw == null || raw.isBlank() ? null : Currency.getInstance(raw.trim().toUpperCase());
    }
}
