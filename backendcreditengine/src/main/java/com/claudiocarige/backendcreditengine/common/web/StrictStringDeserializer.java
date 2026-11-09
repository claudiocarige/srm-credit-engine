package com.claudiocarige.backendcreditengine.common.web;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonToken token = p.currentToken();
        if (token != JsonToken.VALUE_STRING) {
            return (String) ctxt.handleUnexpectedToken(String.class, p);
        }
        return p.getString();
    }
}
