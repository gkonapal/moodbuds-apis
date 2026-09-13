package com.moodbuds.admin;

import java.util.List;
import com.moodbuds.admin.ProductAdminDtos.ImageInput;
import com.moodbuds.admin.ProductAdminDtos.SizeInput;
import com.moodbuds.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductPublicationValidatorTest {
    @Test void completePublishConfigurationIsAccepted(){assertThatCode(()->ProductPublicationValidator.validatePublish(List.of(new SizeInput("M",3,1,true)),images(),List.of(4L))).doesNotThrowAnyException();}
    @Test void draftCollectionsMayBeEmpty(){assertThatCode(()->ProductPublicationValidator.validateCollections(List.of(),List.of(),List.of())).doesNotThrowAnyException();}
    @Test void publishRequiresThreeImages(){assertCode(()->ProductPublicationValidator.validatePublish(List.of(new SizeInput("M",3,1,true)),images().subList(0,2),List.of(4L)),"PRODUCT_IMAGES_REQUIRED");}
    @Test void publishRequiresAvailablePositiveStock(){assertCode(()->ProductPublicationValidator.validatePublish(List.of(new SizeInput("M",0,1,true)),images(),List.of(4L)),"STOCK_REQUIRED");}
    @Test void duplicateSizesAreRejected(){assertCode(()->ProductPublicationValidator.validateCollections(List.of(new SizeInput("M",1,0,true),new SizeInput("M",2,0,true)),images(),List.of(4L)),"DUPLICATE_SIZE");}
    private static List<ImageInput> images(){return List.of(new ImageInput(1,true,0),new ImageInput(2,false,1),new ImageInput(3,false,2));}
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code){assertThatThrownBy(action).isInstanceOf(ApiException.class).extracting(e->((ApiException)e).code()).isEqualTo(code);}
}
