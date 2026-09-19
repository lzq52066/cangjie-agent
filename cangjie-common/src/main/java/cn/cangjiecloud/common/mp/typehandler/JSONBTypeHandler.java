package cn.cangjiecloud.common.mp.typehandler;

import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(ObjectNode.class)
public class JSONBTypeHandler extends BaseTypeHandler<ObjectNode> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ObjectNode parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(JsonUtils.toJSONString(parameter));
        ps.setObject(i, jsonObject);
    }

    @Override
    public ObjectNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public ObjectNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public ObjectNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private ObjectNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parseObject(text);
        } catch (Exception e) {
            return JsonUtils.newObject();
        }
    }
}
