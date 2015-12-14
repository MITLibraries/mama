/**
 * Copyright 2016 MIT Libraries
 * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.mama;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static spark.Spark.*;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.util.IntegerColumnMapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import static com.google.common.base.Strings.*;

/**
 * Mama (metadata ask-me-anything) is a microservice returning a JSON-encoded
 * list of zero or more URIs or specified metadata of DSpace items matching
 * the query in the request, a metadata field/value pair. Unknown fields 404.
 * Request URI format: <server>/item?qf=<field>&qv=<value>[&rf=<field>]*
 * where field = schema.element[.qualifier] and <value> is URL-encoded
 *
 * @author richardrodgers
 */
public class Mama {
    // default response field if none requested - should always have a value
    private static final String URI_FIELD = "dc.identifier.uri";
    // cache of field names <-> field DBIDs
    private static BiMap<String, Integer> fieldIds = HashBiMap.create();

    public static void main(String[] args) {

        Properties props = new Properties();
        props.setProperty("user", System.getenv("MAMA_DB_USER"));
        props.setProperty("password", System.getenv("MAMA_DB_PASSWD"));
        props.setProperty("readOnly", "true");

        DBI dbi = new DBI(System.getenv("MAMA_DB_URL"), props);

        //port(8080);

        before((req, res) -> {
           if (isNullOrEmpty(req.queryParams("qf")) || isNullOrEmpty(req.queryParams("qv"))) {
               halt(400, "Must supply field and value query parameters 'qf' and 'qv'");
           }
        });

        get("/item", (req, res) -> {
            try (Handle hdl = dbi.open()) {
                if (findFieldId(hdl, req.queryParams("qf")) != -1) {
                    res.type("application/json");
                    return "{ " + jsonValue("field", req.queryParams("qf"), true) + ",\n" +
                                  jsonValue("value", req.queryParams("qv"), true) + ",\n" +
                                  jsonValue("items", findItems(hdl, req.queryParams("qf"), req.queryParams("qv"), req.queryParamsValues("rf"))
                                  .stream().collect(Collectors.joining(",", "[", "]")), false) + "\n" + " }";
                } else {
                    res.status(404);
                    return "No such field: " + req.queryParams("qf");
                }
            } catch (Exception e) {
                res.status(500);
                return "Internal system error: " + e.getMessage();
            }
        });
    }

    private static String jsonValue(String name, String value, boolean primitive) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(name).append("\": ");
        if (primitive) sb.append("\""); sb.append(value); if (primitive) sb.append("\"");
        return sb.toString();
    }

    private static String jsonObject(List<Mdv> props) {
        return props.stream().map(p -> jsonValue(p.field, p.value, true)).collect(Collectors.joining(",", "{", "}"));
    }

    private static int findFieldId(Handle hdl, String field) {
        if (fieldIds.containsKey(field)) return fieldIds.get(field);
        String[] parts = field.split("\\.");
        Integer schemaId = hdl.createQuery("select metadata_schema_id from metadataschemaregistry where short_id = ?")
                                          .bind(0, parts[0]).map(IntegerColumnMapper.PRIMITIVE).first();
        if (null != schemaId) {
            Integer fieldId = null;
            if (parts.length == 2) {
                fieldId = hdl.createQuery("select metadata_field_id from metadatafieldregistry where metadata_schema_id = ? and element = ?")
                                          .bind(0, schemaId).bind(1, parts[1]).map(IntegerColumnMapper.PRIMITIVE).first();
            } else if (parts.length == 3) {
                fieldId = hdl.createQuery("select metadata_field_id from metadatafieldregistry where metadata_schema_id = ? and element = ? and qualifier = ?")
                                          .bind(0, schemaId).bind(1, parts[1]).bind(2, parts[2]).map(IntegerColumnMapper.PRIMITIVE).first();
            }
            if (null != fieldId) {
                fieldIds.put(field, fieldId);
                return fieldId;
            }
        }
        return -1;
    }

    private static List<String> findItems(Handle hdl, String qfield, String value, String[] rfields) {
        String queryBase = "select lmv.* from metadatavalue lmv, metadatavalue rmv where " +
                           "lmv.item_id = rmv.item_id and rmv.metadata_field_id = ? and rmv.text_value = ? ";
        Query<Map<String, Object>> query;
        if (null == rfields) { // just return default field
            query = hdl.createQuery(queryBase + "and lmv.metadata_field_id = ?").bind(2, findFieldId(hdl, URI_FIELD));
        } else { // filter out fields we can't resolve
            String inList = Arrays.asList(rfields).stream().map(f -> String.valueOf(findFieldId(hdl, f)))
                                  .filter(id -> id != "-1").collect(Collectors.joining(","));
            query = hdl.createQuery(queryBase + "and lmv.metadata_field_id in (" + inList + ")");
        }
        List<Mdv> rs = query.bind(0, findFieldId(hdl, qfield)).bind(1, value).map(new MdvMapper()).list();
        // group the list by Item, then construct a JSON object for each item
        return rs.stream().collect(Collectors.groupingBy(Mdv::getItemId)).entrySet()
                 .stream().map(Map.Entry::getValue).map(p -> jsonObject(p)).collect(Collectors.toList());
    }

    static class Mdv {
        public int itemId;
        public String field;
        public String value;

        public Mdv(int itemId, String field, String value) {
            this.itemId = itemId;
            this.field = field;
            this.value = value;
        }

        public int getItemId() { return itemId; }
    }

    static class MdvMapper implements ResultSetMapper<Mdv> {
        @Override
        public Mdv map(int index, ResultSet rs, StatementContext ctx) throws SQLException {
            return new Mdv(rs.getInt("item_id"), fieldIds.inverse().get(rs.getInt("metadata_field_id")), rs.getString("text_value"));
        }
    }
}
