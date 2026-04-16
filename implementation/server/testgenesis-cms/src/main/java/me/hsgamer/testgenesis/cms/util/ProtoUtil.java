package me.hsgamer.testgenesis.cms.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

@UtilityClass
@Slf4j
public class ProtoUtil {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public static Map<String, Object> structToMap(Struct struct) {
        String json = structToJson(struct);
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("Failed to parse Struct JSON to Map: {}", json, e);
            return Collections.emptyMap();
        }
    }

    public static String toJson(com.google.protobuf.MessageOrBuilder message) {
        if (message == null) return "{}";
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(message);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to print Message to JSON", e);
            return "{}";
        }
    }
}
