package com.moodbuds.admin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.moodbuds.admin.ProductAdminDtos.ImageInput;
import com.moodbuds.admin.ProductAdminDtos.SizeInput;
import com.moodbuds.common.ApiException;

final class ProductPublicationValidator {
    private static final Set<String> ALLOWED_SIZES=Set.of("XS","S","M","L","XL","XXL","3XL");
    private ProductPublicationValidator() {}

    static void validateCollections(List<SizeInput> sizes,List<ImageInput> images,List<Long> moodIds) {
        if(!unique(sizes.stream().map(SizeInput::size).toList())) throw ApiException.badRequest("DUPLICATE_SIZE","Each size may appear only once");
        if(sizes.stream().anyMatch(size -> !ALLOWED_SIZES.contains(size.size()))) throw ApiException.badRequest("INVALID_SIZE","Size must be XS, S, M, L, XL, XXL, or 3XL");
        if(!unique(images.stream().map(ImageInput::mediaId).toList())) throw ApiException.badRequest("DUPLICATE_IMAGE","Each media image may appear only once");
        if(!unique(moodIds)) throw ApiException.badRequest("DUPLICATE_MOOD","Each mood may appear only once");
        if(images.stream().filter(ImageInput::primary).count()>1) throw ApiException.badRequest("MULTIPLE_PRIMARY_IMAGES","A product can have only one primary image");
    }

    static void validatePublish(List<SizeInput> sizes,List<ImageInput> images,List<Long> moodIds) {
        validateCollections(sizes,images,moodIds);
        if(moodIds.isEmpty()) throw ApiException.badRequest("MOOD_REQUIRED","A published product needs at least one mood");
        if(sizes.isEmpty()) throw ApiException.badRequest("SIZE_REQUIRED","A published product needs at least one size");
        if(sizes.stream().noneMatch(size -> size.available()&&size.stockQuantity()>0)) throw ApiException.badRequest("STOCK_REQUIRED","A published product needs available stock");
        if(images.size()<3) throw ApiException.badRequest("PRODUCT_IMAGES_REQUIRED","A published product needs at least three images");
        if(images.stream().filter(ImageInput::primary).count()!=1) throw ApiException.badRequest("PRIMARY_IMAGE_REQUIRED","A published product needs exactly one primary image");
    }

    private static <T> boolean unique(List<T> values){return new HashSet<>(values).size()==values.size();}
}
