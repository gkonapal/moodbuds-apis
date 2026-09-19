package com.moodbuds.media;

import static com.moodbuds.media.MediaDtos.MediaAsset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final MediaProperties properties;
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public MediaService(MediaProperties properties, JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit,
                        org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.properties=properties; this.jdbc=jdbc; this.namedJdbc=namedJdbc; this.audit=audit;this.transactions=new TransactionTemplate(transactionManager);
    }

    public List<MediaAsset> upload(List<MultipartFile> files, long adminId) {
        if (files == null || files.isEmpty()) throw ApiException.badRequest("IMAGE_REQUIRED", "Upload at least one image");
        if (files.size() > properties.maxImagesPerUpload()) throw ApiException.badRequest("TOO_MANY_IMAGES", "Too many images in one upload");
        return files.stream().map(file -> transactions.execute(status -> uploadOne(file,adminId))).toList();
    }

    private MediaAsset uploadOne(MultipartFile file, long adminId) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_IMAGE", "An uploaded image is empty");
        if (file.getSize() > properties.maxImageSizeBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"IMAGE_TOO_LARGE","Image exceeds the configured size limit");
        byte[] bytes;
        try { bytes=file.getBytes(); } catch (IOException exception) { throw storageFailure(exception); }
        var detected=ImageMetadata.detect(bytes);
        String original=safeFilename(file.getOriginalFilename());
        String key=LocalDate.now(java.time.ZoneOffset.UTC).toString().replace('-','/')+"/"+UUID.randomUUID()+"."+detected.extension();
        Path target=resolve(key);
        writeAtomically(target,bytes);
        try {
            var params=new MapSqlParameterSource().addValue("key",key).addValue("name",original)
                    .addValue("type",detected.contentType()).addValue("size",bytes.length)
                    .addValue("width",detected.width()).addValue("height",detected.height())
                    .addValue("checksum",sha256(bytes)).addValue("admin",adminId);
            var holder=new GeneratedKeyHolder();
            namedJdbc.update("""
                    INSERT INTO media_assets(storage_key,original_filename,content_type,size_bytes,width_px,height_px,checksum_sha256,created_by,created_at)
                    VALUES(:key,:name,:type,:size,:width,:height,:checksum,:admin,UTC_TIMESTAMP())
                    """,params,holder,new String[]{"id"});
            long id=holder.getKey().longValue();
            var asset=get(id);
            audit.record(adminId,"media.image_uploaded","media_asset",id,null,asset);
            return asset;
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw exception;
        }
    }

    public MediaAsset get(long id) {
        return jdbc.sql("SELECT id,original_filename,content_type,size_bytes,width_px,height_px,created_at FROM media_assets WHERE id=:id")
                .param("id",id).query((rs,n) -> new MediaAsset(rs.getLong("id"),url(rs.getLong("id")),rs.getString("original_filename"),
                        rs.getString("content_type"),rs.getLong("size_bytes"),nullableInt(rs,"width_px"),nullableInt(rs,"height_px"),rs.getObject("created_at",LocalDateTime.class)))
                .optional().orElseThrow(() -> ApiException.notFound("Media asset"));
    }

    private Integer nullableInt(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }

    StoredMedia content(long id) {
        return jdbc.sql("SELECT storage_key,content_type,original_filename FROM media_assets WHERE id=:id").param("id",id)
                .query((rs,n) -> new StoredMedia(resolve(rs.getString("storage_key")),rs.getString("content_type"),rs.getString("original_filename")))
                .optional().orElseThrow(() -> ApiException.notFound("Media asset"));
    }

    @Transactional
    public void delete(long id,long adminId) {
        StoredMedia stored=content(id);
        int uses=jdbc.sql("SELECT (SELECT COUNT(*) FROM product_images WHERE media_asset_id=:id)+(SELECT COUNT(*) FROM size_charts WHERE media_asset_id=:id)")
                .param("id",id).query(Integer.class).single();
        if(uses>0) throw new ApiException(HttpStatus.CONFLICT,"MEDIA_IN_USE","Remove this image from all products and size charts before deleting it");
        jdbc.sql("DELETE FROM media_assets WHERE id=:id").param("id",id).update();
        try { Files.deleteIfExists(stored.path()); } catch(IOException exception) { throw storageFailure(exception); }
        audit.record(adminId,"media.image_deleted","media_asset",id,null,null);
    }

    public String url(long id) { return "/api/v1/media/"+id+"/content"; }

    private Path resolve(String key) {
        Path path=properties.root().resolve(key.replace('/',java.io.File.separatorChar)).normalize();
        if(!path.startsWith(properties.root())) throw new IllegalStateException("Invalid media storage key");
        return path;
    }
    private void writeAtomically(Path target,byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary=Files.createTempFile(target.getParent(),"upload-",".tmp");
            Files.write(temporary,bytes);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE); }
            catch(java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException exception) { throw storageFailure(exception); }
    }
    private String safeFilename(String name) {
        if(name==null||name.isBlank()) return "image";
        String clean=Path.of(name).getFileName().toString().replaceAll("[\\r\\n]","");
        return clean.length()>255?clean.substring(clean.length()-255):clean;
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private ApiException storageFailure(Exception cause) { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"MEDIA_STORAGE_ERROR","The image could not be stored"); }

    record StoredMedia(Path path,String contentType,String originalFilename) {
        InputStream inputStream() throws IOException { return Files.newInputStream(path); }
    }
}
