package com.reedelk.mongodb.component;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.reedelk.mongodb.internal.ClientFactory;
import com.reedelk.mongodb.internal.attribute.UpdateAttributes;
import com.reedelk.mongodb.internal.commons.DocumentUtils;
import com.reedelk.mongodb.internal.commons.Unsupported;
import com.reedelk.mongodb.internal.exception.MongoDBDocumentException;
import com.reedelk.mongodb.internal.exception.MongoDBUpdateException;
import com.reedelk.runtime.api.annotation.*;
import com.reedelk.runtime.api.component.ProcessorSync;
import com.reedelk.runtime.api.converter.ConverterService;
import com.reedelk.runtime.api.flow.FlowContext;
import com.reedelk.runtime.api.message.Message;
import com.reedelk.runtime.api.message.MessageAttributes;
import com.reedelk.runtime.api.message.MessageBuilder;
import com.reedelk.runtime.api.message.content.Pair;
import com.reedelk.runtime.api.script.ScriptEngineService;
import com.reedelk.runtime.api.script.dynamicvalue.DynamicObject;
import org.bson.Document;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import java.util.List;
import java.util.Map;

import static com.reedelk.mongodb.internal.commons.Messages.Update.UPDATE_DOCUMENT_EMPTY;
import static com.reedelk.mongodb.internal.commons.Messages.Update.UPDATE_QUERY_NULL;
import static com.reedelk.mongodb.internal.commons.Utils.evaluateOrUsePayloadWhenEmpty;
import static com.reedelk.mongodb.internal.commons.Utils.isTrue;
import static com.reedelk.runtime.api.commons.ComponentPrecondition.Configuration.requireNotBlank;
import static com.reedelk.runtime.api.commons.ComponentPrecondition.Configuration.requireNotNullOrBlank;

@ModuleComponent("MongoDB Update (One/Many)")
@Component(service = Update.class, scope = ServiceScope.PROTOTYPE)
@ComponentOutput(
        attributes = UpdateAttributes.class,
        payload = long.class,
        description = "The number of updated documents.")
@ComponentInput(
        payload = { List.class, String.class, Map.class, Pair.class, byte[].class },
        description = "The updated data of the document to be updated in MongoDB.")
@Description("Updates one or more documents into the given database collection. " +
        "The connection configuration allows to specify host, port, database name, username and password to be used for authentication against the database. " +
        "If the query filter expression is not empty, the query will be used to match only the document/s to be updated with the update document. " +
        "The update document can be a static or a dynamic expression. " +
        "The update document might be a JSON string, a Map, a Pair or a DataRow (Update One)." +
        "If the property many is true, <b>all</b> the documents matching the " +
        "given query filter will be updated (Update Many).")
public class Update implements ProcessorSync {

    @DialogTitle("MongoDB Connection")
    @Property("Connection")
    @Description("MongoDB connection configuration to be used by this update operation. " +
            "Shared configurations use the same MongoDB client.")
    private ConnectionConfiguration connection;

    @Property("Collection")
    @Hint("MyCollection")
    @Example("MyCollection")
    @Description("Sets the name of the collection to be used for the update operation.")
    private String collection;

    @Property("Query Filter")
    @Hint("{ item: \"BLP921\" }")
    @InitValue("{ _id: 1 }")
    @Example("<ul>" +
            "<li>{ _id: 1 }</li>" +
            "<li><code>{ _id: message.attributes().id }</code></li>" +
            "<li>{ name: \"Andy\" }</li>" +
            "</ul>")
    @Description("Sets the selection criteria for the update. It could be a static or dynamic value.")
    private DynamicObject query;

    @Property("Update Document")
    @InitValue("#[message.payload()]")
    @DefaultValue("#[message.payload()")
    @Description("The update document to be used to update one or all the documents matching the query filter.")
    private DynamicObject document;

    @Property("Update Many")
    @Example("true")
    @DefaultValue("false")
    @Description("If true updates all the documents matching the query filter, otherwise only one will be updated.")
    private Boolean many;

    @Reference
    ConverterService converterService;
    @Reference
    ScriptEngineService scriptService;
    @Reference
    ClientFactory clientFactory;

    private MongoClient client;

    @Override
    public void initialize() {
        requireNotBlank(Update.class, collection, "Collection must not be empty");
        requireNotNullOrBlank(Update.class, query, "Query filter must not be empty");
        this.client = clientFactory.clientByConfig(this, connection);
    }

    @Override
    public Message apply(FlowContext flowContext, Message message) {

        MongoDatabase mongoDatabase = client.getDatabase(connection.getDatabase());
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

        Object evaluatedQuery = scriptService.evaluate(query, flowContext, message)
                .orElseThrow(() -> new MongoDBUpdateException(UPDATE_QUERY_NULL.format(query.value())));

        Object toUpdate =
                evaluateOrUsePayloadWhenEmpty(document, scriptService, flowContext, message,
                        () -> new MongoDBDocumentException(UPDATE_DOCUMENT_EMPTY.format(document.value())));

        UpdateResult updateResult;

        // Update without pipeline
        Document toUpdateQuery = DocumentUtils.from(converterService, evaluatedQuery, Unsupported.queryType(evaluatedQuery));
        Document toUpdateDocument = DocumentUtils.from(converterService, toUpdate, Unsupported.documentType(toUpdate));

        updateResult = isTrue(many) ?
                mongoCollection.updateMany(toUpdateQuery, toUpdateDocument) :
                mongoCollection.updateOne(toUpdateQuery, toUpdateDocument);

        long modifiedCount = updateResult.getModifiedCount();

        MessageAttributes attributes = new UpdateAttributes(updateResult);

        return MessageBuilder.get(Update.class)
                .withJavaObject(modifiedCount) // Body contains modified count.
                .attributes(attributes)
                .build();
    }

    @Override
    public void dispose() {
        clientFactory.dispose(this, connection);
        client = null;
    }

    public void setConnection(ConnectionConfiguration connection) {
        this.connection = connection;
    }

    public void setDocument(DynamicObject document) {
        this.document = document;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setQuery(DynamicObject query) {
        this.query = query;
    }

    public void setMany(Boolean many) {
        this.many = many;
    }
}
