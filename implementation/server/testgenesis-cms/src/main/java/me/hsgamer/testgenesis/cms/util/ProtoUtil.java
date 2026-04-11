package me.hsgamer.testgenesis.cms.util;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ProtoUtil {
    public static Struct jsonToStruct(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Struct.newBuilder().build();
        }
        try {
            Struct.Builder builder = Struct.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
            return builder.build();
        } catch (InvalidProtocolBufferException e) {

            log.error("Failed to parse JSON to Struct: {}", json, e);
            return Struct.newBuilder().build();
        }
    }

    public static String structToJson(Struct struct) {
        if (struct == null || struct.getFieldsCount() == 0) {
            return "{}";
        }
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(struct);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to print Struct to JSON", e);
            return "{}";
        }
    }
}
