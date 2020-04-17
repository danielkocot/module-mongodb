package com.reedelk.mongodb.internal.exception;

import com.reedelk.runtime.api.exception.PlatformException;

public class MongoDBDocumentException extends PlatformException {

    public MongoDBDocumentException(String message) {
        super(message);
    }
}
