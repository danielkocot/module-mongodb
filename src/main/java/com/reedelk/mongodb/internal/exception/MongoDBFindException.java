package com.reedelk.mongodb.internal.exception;

import com.reedelk.runtime.api.exception.PlatformException;

public class MongoDBFindException extends PlatformException {

    public MongoDBFindException(String message) {
        super(message);
    }
}
