package com.moodbuds.admin;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.common.SlugService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCrudService {
    private final AdminResourceRegistry registry;
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final SlugService slugService;
    private final AuditService audit;

    public AdminCrudService(AdminResourceRegistry registry, JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc,
                            SlugService slugService, AuditService audit) {
        this.registry=registry; this.jdbc=jdbc; this.namedJdbc=namedJdbc; this.slugService=slugService; this.audit=audit;
    }

    public PageResponse<Map<String,Object>> list(String resource, int page, int size, String query) {
        var def=registry.require(resource);
        int safePage=Math.max(page,0), safeSize=Math.min(Math.max(size,1),100);
        String where=(query==null||query.isBlank()) ? "" : " WHERE "+def.displayField()+" LIKE :query";
        var sql=jdbc.sql("SELECT * FROM "+def.table()+where+" ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("limit",safeSize).param("offset",safePage*safeSize);
        var count=jdbc.sql("SELECT COUNT(*) FROM "+def.table()+where);
        if (!where.isEmpty()) { sql=sql.param("query","%"+query.trim()+"%"); count=count.param("query","%"+query.trim()+"%"); }
        return PageResponse.of(sql.query().listOfRows(),safePage,safeSize,count.query(Long.class).single());
    }

    public Map<String,Object> get(String resource,long id) {
        var def=registry.require(resource);
        return jdbc.sql("SELECT * FROM "+def.table()+" WHERE id=:id").param("id",id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound(resource));
    }

    @Transactional
    public Map<String,Object> create(String resource,Map<String,Object> body,long actorId) {
        var def=registry.require(resource);
        var values=allowedValues(def,body);
        if (def.slugged()) values.put("slug",slugService.uniqueSlug(def.table(),string(values.get("slug")),string(values.get("name")),null));
        if (def.createdBy()) values.put("created_by",actorId);
        if (values.isEmpty()) throw ApiException.badRequest("EMPTY_REQUEST","At least one supported field is required");
        String columns=String.join(",",values.keySet());
        String params=values.keySet().stream().map(k -> ":"+k).collect(Collectors.joining(","));
        var keyHolder=new GeneratedKeyHolder();
        namedJdbc.update("INSERT INTO "+def.table()+" ("+columns+") VALUES ("+params+")",
                new MapSqlParameterSource(values),keyHolder,new String[]{"id"});
        long id=keyHolder.getKey().longValue();
        var created=get(resource,id);
        audit.record(actorId,resource+".created",def.table(),id,null,created);
        return created;
    }

    @Transactional
    public Map<String,Object> update(String resource,long id,Map<String,Object> body,long actorId) {
        var def=registry.require(resource);
        var old=get(resource,id);
        if ("products".equals(def.table()) && "ARCHIVED".equals(String.valueOf(old.get("publication_status")))) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"PRODUCT_ARCHIVED","An archived product cannot be edited");
        }
        var values=allowedValues(def,body);
        if (def.slugged() && (values.containsKey("slug") || values.containsKey("name") && old.get("slug")==null)) {
            String requested=values.containsKey("slug")?string(values.get("slug")):string(old.get("slug"));
            String name=values.containsKey("name")?string(values.get("name")):string(old.get("name"));
            values.put("slug",slugService.uniqueSlug(def.table(),requested,name,id));
        }
        if (values.isEmpty()) throw ApiException.badRequest("EMPTY_REQUEST","At least one supported field is required");
        String assignments=values.keySet().stream().map(k -> k+"=:"+k).collect(Collectors.joining(","));
        values.put("id",id);
        namedJdbc.update("UPDATE "+def.table()+" SET "+assignments+" WHERE id=:id",values);
        var updated=get(resource,id);
        audit.record(actorId,resource+".updated",def.table(),id,old,updated);
        return updated;
    }

    @Transactional
    public void delete(String resource,long id,long actorId) {
        var def=registry.require(resource);
        var old=get(resource,id);
        int changed = def.softDelete()
                ? jdbc.sql("UPDATE "+def.table()+" SET is_active=0"+("products".equals(def.table())?",publication_status='ARCHIVED'":"")+" WHERE id=:id").param("id",id).update()
                : jdbc.sql("DELETE FROM "+def.table()+" WHERE id=:id").param("id",id).update();
        if(changed==0) throw ApiException.notFound(resource);
        audit.record(actorId,resource+"."+(def.softDelete()?"deactivated":"deleted"),def.table(),id,old,null);
    }

    private LinkedHashMap<String,Object> allowedValues(ResourceDefinition def,Map<String,Object> body) {
        var values=new LinkedHashMap<String,Object>();
        body.forEach((key,value) -> {
            String normalized = toSnakeCase(key);
            if(def.fields().contains(normalized)) values.put(normalized,value);
        });
        return values;
    }
    private String string(Object value) { return value==null?null:String.valueOf(value); }
    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
}
