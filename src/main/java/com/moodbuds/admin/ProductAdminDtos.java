package com.moodbuds.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ProductAdminDtos {
    private ProductAdminDtos() {}

    public enum PublicationAction { SAVE_DRAFT, PUBLISH }
    public enum PublicationStatus { DRAFT, PUBLISHED, ARCHIVED }

    public record CompleteProductRequest(
            @NotNull @Valid ProductInput product,
            @Valid @Size(max=7) List<SizeInput> sizes,
            @Valid @Size(max=20) List<ImageInput> images,
            @Size(max=50) List<@Positive Long> moodIds,
            @Positive Long sizeChartMediaId,
            @NotNull PublicationAction publicationAction) {
        public CompleteProductRequest {
            sizes=sizes==null?List.of():List.copyOf(sizes);
            images=images==null?List.of():List.copyOf(images);
            moodIds=moodIds==null?List.of():List.copyOf(moodIds);
        }
    }

    public record ProductInput(
            @NotBlank @Size(max=100) String sku,
            @NotBlank @Size(max=300) String name,
            @Size(max=360) String slug,
            @Positive long categoryId,
            @Positive long subcategoryId,
            @Positive long gstRateId,
            String description,
            @Size(max=500) String fabricDetails,
            @Size(max=100) String colorName,
            @Positive long price,
            @Positive Long discountPrice,
            @Positive Long weightGrams,
            @Positive BigDecimal lengthCm,
            @Positive BigDecimal widthCm,
            @Positive BigDecimal heightCm,
            boolean featured,
            boolean newArrival,
            boolean bestSeller,
            @PositiveOrZero @Max(90) int returnWindowDays) {}

    public record SizeInput(@NotBlank String size,@PositiveOrZero int stockQuantity,
                            @PositiveOrZero int lowStockThreshold,boolean available) {}
    public record ImageInput(@Positive Long id,@Positive Long mediaId,boolean primary,@PositiveOrZero int sortOrder) {
        public ImageInput(long mediaId, boolean primary, int sortOrder) {
            this(null, mediaId, primary, sortOrder);
        }
    }

    public record CompleteProductResponse(ProductView product,List<SizeView> sizes,List<ImageView> images,
                                          List<Long> moodIds,SizeChartView sizeChart) {}
    public record ProductView(long id,String sku,String name,String slug,long categoryId,long subcategoryId,long gstRateId,
                              String description,String fabricDetails,String colorName,long price,Long discountPrice,
                              Long weightGrams,BigDecimal lengthCm,BigDecimal widthCm,BigDecimal heightCm,
                              boolean featured,boolean newArrival,boolean bestSeller,int returnWindowDays,
                              PublicationStatus publicationStatus,LocalDateTime publishedAt,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record SizeView(long id,String size,int stockQuantity,int lowStockThreshold,boolean available) {}
    public record ImageView(long id,Long mediaId,String imageUrl,boolean primary,int sortOrder) {}
    public record SizeChartView(long id,long mediaId,String imageUrl) {}
}
