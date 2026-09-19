package com.moodbuds.media;

import static com.moodbuds.media.MediaDtos.MediaAsset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
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

    private static final java.util.Set<String> ALLOWED_KINDS = java.util.Set.of("products", "banners");
    private static final String TEMP_PREFIX = "temp/";

    /**
     * Upload files to temporary storage. Files must be finalized via finalizeTempFiles()
     * before they can be used in products. This ensures orphaned files are prevented.
     */
    public List<MediaAsset> upload(List<MultipartFile> files, long adminId, String kind) {
        if (files == null || files.isEmpty()) throw ApiException.badRequest("IMAGE_REQUIRED", "Upload at least one image");
        if (files.size() > properties.maxImagesPerUpload()) throw ApiException.badRequest("TOO_MANY_IMAGES", "Too many images in one upload");
        String folder = (kind == null || kind.isBlank()) ? "products" : kind.toLowerCase();
        if (!ALLOWED_KINDS.contains(folder)) throw ApiException.badRequest("INVALID_MEDIA_KIND", "Media kind must be one of " + ALLOWED_KINDS);
        // Save to temp folder
        String tempFolder = TEMP_PREFIX + folder;
        return files.stream().map(file -> transactions.execute(status -> uploadOne(file,adminId,tempFolder))).toList();
    }

    private MediaAsset uploadOne(MultipartFile file, long adminId, String folder) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_IMAGE", "An uploaded image is empty");
        if (file.getSize() > properties.maxImageSizeBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"IMAGE_TOO_LARGE","Image exceeds the configured size limit");
        byte[] bytes;
        try { bytes=file.getBytes(); } catch (IOException exception) { throw storageFailure(exception); }
        var detected=ImageMetadata.detect(bytes);
        String original=safeFilename(file.getOriginalFilename());
        String key=folder+"/"+UUID.randomUUID()+"."+detected.extension();
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
                    VALUES(:key,:name,:type,:size,:width,:height,:checksum,:admin,CURRENT_TIMESTAMP())
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

    /**
     * Finalize temporary files by moving them to permanent storage.
     * Call this within the product creation or update transaction.
     * Safe to call even if files are already finalized (idempotent).
     */
    @Transactional
    public void finalizeTempFiles(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return;
        
        for (Long id : mediaIds) {
            try {
                var row = jdbc.sql("SELECT storage_key FROM media_assets WHERE id=:id")
                        .param("id", id)
                        .query((rs, n) -> rs.getString("storage_key"))
                        .optional();
                
                if (row.isEmpty()) {
                    System.err.println("Media ID " + id + " not found in database");
                    continue;
                }
                
                String storageKey = row.get();
                if (!storageKey.startsWith(TEMP_PREFIX)) {
                    // Already finalized or not a temp file - skip safely
                    System.out.println("Media ID " + id + " already finalized (key: " + storageKey + ")");
                    continue;
                }
                
                // Move from temp/category/uuid.ext → category/uuid.ext
                String permanentKey = storageKey.substring(TEMP_PREFIX.length());
                Path tempPath = resolve(storageKey);
                Path permanentPath = resolve(permanentKey);
                
                System.out.println("Finalizing media " + id + ": moving from " + tempPath + " to " + permanentPath);
                
                try {
                    if (!Files.exists(tempPath)) {
                        System.err.println("ERROR: Temp file does not exist: " + tempPath);
                        continue;
                    }
                    
                    Files.createDirectories(permanentPath.getParent());
                    Files.move(tempPath, permanentPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    if (!Files.exists(permanentPath)) {
                        System.err.println("ERROR: File move verification failed for " + permanentPath);
                        continue;
                    }
                    
                    // Update storage_key in database
                    jdbc.sql("UPDATE media_assets SET storage_key=:key WHERE id=:id")
                            .param("key", permanentKey)
                            .param("id", id)
                            .update();
                    
                    System.out.println("Successfully finalized media " + id + " at " + permanentPath);
                } catch (IOException e) {
                    System.err.println("IOException finalizing media " + id + ": " + e.getMessage());
                    e.printStackTrace();
                    throw storageFailure(e);
                }
            } catch (Exception e) {
                System.err.println("Error: Failed to finalize temp file for media ID " + id + ": " + e.getMessage());
                e.printStackTrace();
                // Don't silently continue - fail the transaction so we know there's an issue
                throw new RuntimeException("Failed to finalize media files. Images won't be accessible.", e);
            }
        }
    }

    /**
     * Delete temporary files that were not finalized (orphaned uploads).
     * Call this after transaction completes to clean up uploaded temp files.
     */
    public void cleanupTempFiles(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return;
        
        for (Long id : mediaIds) {
            var row = jdbc.sql("SELECT storage_key FROM media_assets WHERE id=:id")
                    .param("id", id)
                    .query((rs, n) -> rs.getString("storage_key"))
                    .optional();
            
            if (row.isEmpty()) continue;
            
            String key = row.get();
            if (!key.startsWith(TEMP_PREFIX)) continue; // Not a temp file
            
            Path tempPath = resolve(key);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                // Log but don't fail - this is cleanup
                System.err.println("Failed to cleanup temp file " + tempPath + ": " + e.getMessage());
            }
        }
    }

    /**
     * Cleanup orphaned temporary files older than the specified age.
     * This is a scheduled task for abandoned uploads that were never completed.
     * Safe to call periodically (e.g., via @Scheduled).
     */
    public void cleanupOrphanedTempFiles(java.time.Duration maxAge) {
        try {
            Path tempDir = properties.root().resolve(TEMP_PREFIX);
            if (!Files.exists(tempDir)) return;
            
            long cutoffTime = System.currentTimeMillis() - maxAge.toMillis();
            java.nio.file.Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            System.out.println("Cleaned up orphaned temp file: " + p);
                        } catch (IOException e) {
                            System.err.println("Failed to delete orphaned temp file " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error during orphaned temp file cleanup: " + e.getMessage());
        }
    }

    public MediaAsset get(long id) {
        return jdbc.sql("SELECT id,original_filename,content_type,size_bytes,width_px,height_px,created_at FROM media_assets WHERE id=:id")
                .param("id",id).query((rs,n) -> new MediaAsset(rs.getLong("id"),url(rs.getLong("id")),rs.getString("original_filename"),
                        rs.getString("content_type"),rs.getLong("size_bytes"),(Integer)rs.getObject("width_px"),(Integer)rs.getObject("height_px"),rs.getObject("created_at",LocalDateTime.class)))
                .optional().orElseThrow(() -> ApiException.notFound("Media asset"));
    }

    StoredMedia content(long id) {
        var media = jdbc.sql("SELECT storage_key,content_type,original_filename FROM media_assets WHERE id=:id").param("id",id)
                .query((rs,n) -> new StoredMedia(resolve(rs.getString("storage_key")),rs.getString("content_type"),rs.getString("original_filename")))
                .optional().orElseThrow(() -> ApiException.notFound("Media asset"));
        
        // Verify file actually exists on disk (it may have been deleted)
        if (!Files.exists(media.path())) {
            throw ApiException.notFound("Media file no longer exists on disk");
        }
        
        return media;
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
